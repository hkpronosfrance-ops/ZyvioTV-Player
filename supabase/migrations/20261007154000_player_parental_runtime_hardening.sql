-- Final parental runtime hardening.
-- Track active playback per device so one device pausing cannot reset another
-- device's active Child playback. Reconcile offline usage using a monotonic
-- high-water mark supplied by the client; the server never decreases usage.

create table if not exists public.player_profile_playback_sessions (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.player_profiles(id) on delete cascade,
  device_uid text not null,
  playing boolean not null default false,
  last_seen_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (profile_id, device_uid),
  unique (user_id, profile_id, device_uid)
);

create index if not exists player_profile_playback_sessions_active_idx
  on public.player_profile_playback_sessions (profile_id, playing, last_seen_at desc);

alter table public.player_profile_playback_sessions enable row level security;
revoke all on public.player_profile_playback_sessions from anon, authenticated;

create or replace function public.player_parental_screen_time_heartbeat_v2(
  p_profile_id uuid,
  p_device_uid text,
  p_playing boolean,
  p_content_key text default '',
  p_local_consumed_seconds integer default 0
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
  v_was_active boolean := false;
  v_is_active boolean := false;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_device_uid is null or length(trim(p_device_uid)) = 0 or length(p_device_uid) > 200 then
    raise exception 'invalid_device_uid';
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
  values (v_user_id, p_profile_id, v_today, 0, null)
  on conflict (profile_id, usage_date) do nothing;

  select * into v_row
  from public.player_profile_screen_time_daily
  where profile_id = p_profile_id and usage_date = v_today
  for update;

  -- Before mutating this device, determine whether any device was actively
  -- playing during the interval since the previous accounting cursor.
  select exists (
    select 1
    from public.player_profile_playback_sessions s
    where s.profile_id = p_profile_id
      and s.user_id = v_user_id
      and s.playing
      and s.last_seen_at >= v_now - interval '90 seconds'
  ) into v_was_active;

  if v_was_active and v_row.last_accounted_at is not null then
    v_delta := least(
      greatest(extract(epoch from (v_now - v_row.last_accounted_at))::integer, 0),
      90
    );
  end if;

  insert into public.player_profile_playback_sessions (
    user_id, profile_id, device_uid, playing, last_seen_at, updated_at
  )
  values (
    v_user_id, p_profile_id, trim(p_device_uid), p_playing, v_now, v_now
  )
  on conflict (profile_id, device_uid) do update
    set playing = excluded.playing,
        last_seen_at = excluded.last_seen_at,
        updated_at = excluded.updated_at,
        user_id = excluded.user_id;

  select exists (
    select 1
    from public.player_profile_playback_sessions s
    where s.profile_id = p_profile_id
      and s.user_id = v_user_id
      and s.playing
      and s.last_seen_at >= v_now - interval '90 seconds'
  ) into v_is_active;

  update public.player_profile_screen_time_daily
  set consumed_seconds = greatest(
        consumed_seconds + v_delta,
        greatest(coalesce(p_local_consumed_seconds, 0), 0)
      ),
      last_accounted_at = case when v_is_active then v_now else null end,
      updated_at = v_now
  where profile_id = p_profile_id and usage_date = v_today;

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
    'usage_date', v_today,
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

revoke all on function public.player_parental_screen_time_heartbeat_v2(uuid, text, boolean, text, integer)
  from public;
grant execute on function public.player_parental_screen_time_heartbeat_v2(uuid, text, boolean, text, integer)
  to authenticated;
