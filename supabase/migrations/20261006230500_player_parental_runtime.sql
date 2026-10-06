-- Runtime enforcement for profile-scoped parental controls.
-- Screen time is accounted once per profile even with simultaneous devices:
-- all heartbeats serialize through one daily row and advance one shared clock.

create table if not exists public.player_profile_screen_time_daily (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.player_profiles(id) on delete cascade,
  usage_date date not null,
  consumed_seconds integer not null default 0 check (consumed_seconds >= 0),
  last_accounted_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (profile_id, usage_date),
  unique (user_id, profile_id, usage_date)
);

create table if not exists public.player_parental_exceptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.player_profiles(id) on delete cascade,
  content_key text not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_player_parental_exceptions_active
  on public.player_parental_exceptions (profile_id, expires_at desc);

alter table public.player_profile_screen_time_daily enable row level security;
alter table public.player_parental_exceptions enable row level security;
revoke all on public.player_profile_screen_time_daily from anon, authenticated;
revoke all on public.player_parental_exceptions from anon, authenticated;

create or replace function public.player_parental_runtime_state(
  p_profile_id uuid,
  p_content_key text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_profile public.player_profiles%rowtype;
  v_control public.player_parental_controls%rowtype;
  v_settings public.player_profile_parental_settings%rowtype;
  v_today date := (now() at time zone 'UTC')::date;
  v_used integer := 0;
  v_limit integer;
  v_exception_until timestamptz;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select * into v_profile
  from public.player_profiles
  where id = p_profile_id and user_id = v_user_id;

  if not found then
    raise exception 'profile_not_found';
  end if;

  select * into v_control
  from public.player_parental_controls
  where user_id = v_user_id;

  select * into v_settings
  from public.player_profile_parental_settings
  where profile_id = p_profile_id and user_id = v_user_id;

  select consumed_seconds into v_used
  from public.player_profile_screen_time_daily
  where profile_id = p_profile_id and usage_date = v_today;

  v_used := coalesce(v_used, 0);

  if extract(isodow from now()) in (6, 7) then
    v_limit := coalesce(v_settings.weekend_limit_minutes, v_settings.daily_limit_minutes);
  else
    v_limit := v_settings.daily_limit_minutes;
  end if;

  if nullif(p_content_key, '') is not null then
    select max(expires_at) into v_exception_until
    from public.player_parental_exceptions
    where profile_id = p_profile_id
      and user_id = v_user_id
      and content_key = p_content_key
      and expires_at > now();
  end if;

  return jsonb_build_object(
    'parental_enabled', coalesce(v_control.enabled, false),
    'is_child', v_profile.profile_type = 'child',
    'consumed_seconds', v_used,
    'limit_minutes', v_limit,
    'warning_minutes', coalesce(v_settings.warning_minutes, 10),
    'schedule_enabled', coalesce(v_settings.schedule_enabled, false),
    'schedule_windows', coalesce(v_settings.schedule_windows, '[]'::jsonb),
    'exception_until', v_exception_until,
    'blocked_by_time',
      coalesce(v_control.enabled, false)
      and v_profile.profile_type = 'child'
      and v_limit is not null
      and v_used >= v_limit * 60
      and v_exception_until is null
  );
end;
$$;

create or replace function public.player_parental_screen_time_heartbeat(
  p_profile_id uuid,
  p_playing boolean,
  p_content_key text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_profile public.player_profiles%rowtype;
  v_control public.player_parental_controls%rowtype;
  v_settings public.player_profile_parental_settings%rowtype;
  v_today date := (now() at time zone 'UTC')::date;
  v_now timestamptz := now();
  v_row public.player_profile_screen_time_daily%rowtype;
  v_delta integer := 0;
  v_limit integer;
  v_exception_until timestamptz;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select * into v_profile
  from public.player_profiles
  where id = p_profile_id and user_id = v_user_id;

  if not found then
    raise exception 'profile_not_found';
  end if;

  select * into v_control
  from public.player_parental_controls
  where user_id = v_user_id;

  select * into v_settings
  from public.player_profile_parental_settings
  where profile_id = p_profile_id and user_id = v_user_id;

  insert into public.player_profile_screen_time_daily (
    user_id, profile_id, usage_date, consumed_seconds, last_accounted_at
  )
  values (
    v_user_id, p_profile_id, v_today, 0,
    case when p_playing then v_now else null end
  )
  on conflict (profile_id, usage_date) do nothing;

  select * into v_row
  from public.player_profile_screen_time_daily
  where profile_id = p_profile_id and usage_date = v_today
  for update;

  if p_playing then
    if v_row.last_accounted_at is not null then
      -- Clamp to the heartbeat window. This avoids charging time while an app
      -- was suspended or disconnected and also prevents simultaneous devices
      -- from double-counting the same wall-clock interval.
      v_delta := least(
        greatest(extract(epoch from (v_now - v_row.last_accounted_at))::integer, 0),
        90
      );
    end if;

    update public.player_profile_screen_time_daily
    set consumed_seconds = consumed_seconds + v_delta,
        last_accounted_at = v_now,
        updated_at = v_now
    where profile_id = p_profile_id and usage_date = v_today;
  else
    update public.player_profile_screen_time_daily
    set last_accounted_at = null,
        updated_at = v_now
    where profile_id = p_profile_id and usage_date = v_today;
  end if;

  select * into v_row
  from public.player_profile_screen_time_daily
  where profile_id = p_profile_id and usage_date = v_today;

  if extract(isodow from v_now) in (6, 7) then
    v_limit := coalesce(v_settings.weekend_limit_minutes, v_settings.daily_limit_minutes);
  else
    v_limit := v_settings.daily_limit_minutes;
  end if;

  if nullif(p_content_key, '') is not null then
    select max(expires_at) into v_exception_until
    from public.player_parental_exceptions
    where profile_id = p_profile_id
      and user_id = v_user_id
      and content_key = p_content_key
      and expires_at > v_now;
  end if;

  return jsonb_build_object(
    'consumed_seconds', v_row.consumed_seconds,
    'limit_minutes', v_limit,
    'warning_minutes', coalesce(v_settings.warning_minutes, 10),
    'exception_until', v_exception_until,
    'blocked_by_time',
      coalesce(v_control.enabled, false)
      and v_profile.profile_type = 'child'
      and v_limit is not null
      and v_row.consumed_seconds >= v_limit * 60
      and v_exception_until is null
  );
end;
$$;

create or replace function public.player_parental_grant_exception(
  p_profile_id uuid,
  p_pin text,
  p_content_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_control public.player_parental_controls%rowtype;
  v_profile public.player_profiles%rowtype;
  v_now timestamptz := now();
  v_expires timestamptz := now() + interval '30 minutes';
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_content_key is null or length(trim(p_content_key)) = 0 or length(p_content_key) > 300 then
    return jsonb_build_object('success', false, 'reason', 'invalid_content');
  end if;

  select * into v_profile
  from public.player_profiles
  where id = p_profile_id and user_id = v_user_id;

  if not found then
    return jsonb_build_object('success', false, 'reason', 'profile_not_found');
  end if;

  select * into v_control
  from public.player_parental_controls
  where user_id = v_user_id
  for update;

  if not found or v_control.pin_hash is null then
    return jsonb_build_object('success', false, 'reason', 'pin_not_configured');
  end if;

  if v_control.blocked_until is not null and v_control.blocked_until > v_now then
    return jsonb_build_object('success', false, 'reason', 'blocked');
  end if;

  if p_pin !~ '^[0-9]{4}$'
     or extensions.crypt(p_pin, v_control.pin_hash) <> v_control.pin_hash then
    return jsonb_build_object('success', false, 'reason', 'pin_invalid');
  end if;

  delete from public.player_parental_exceptions
  where user_id = v_user_id
    and profile_id = p_profile_id
    and content_key = p_content_key;

  insert into public.player_parental_exceptions (
    user_id, profile_id, content_key, expires_at
  )
  values (v_user_id, p_profile_id, p_content_key, v_expires);

  return jsonb_build_object(
    'success', true,
    'expires_at', v_expires
  );
end;
$$;

create or replace function public.player_parental_end_exception(
  p_profile_id uuid,
  p_content_key text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  delete from public.player_parental_exceptions
  where user_id = v_user_id
    and profile_id = p_profile_id
    and content_key = p_content_key;
end;
$$;

revoke all on function public.player_parental_runtime_state(uuid, text) from public;
revoke all on function public.player_parental_screen_time_heartbeat(uuid, boolean, text) from public;
revoke all on function public.player_parental_grant_exception(uuid, text, text) from public;
revoke all on function public.player_parental_end_exception(uuid, text) from public;

grant execute on function public.player_parental_runtime_state(uuid, text) to authenticated;
grant execute on function public.player_parental_screen_time_heartbeat(uuid, boolean, text) to authenticated;
grant execute on function public.player_parental_grant_exception(uuid, text, text) to authenticated;
grant execute on function public.player_parental_end_exception(uuid, text) to authenticated;
