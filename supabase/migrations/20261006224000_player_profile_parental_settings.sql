-- Advanced profile-scoped parental restrictions.
-- The PIN and global parental enable switch remain account-scoped.
-- Age, masking, screen-time and schedule rules are profile-scoped.

alter table public.player_parental_controls
  add column if not exists enabled boolean not null default false;

create table if not exists public.player_profile_parental_settings (
  profile_id uuid primary key references public.player_profiles(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  hide_locked boolean not null default false,
  daily_limit_minutes integer check (
    daily_limit_minutes is null or daily_limit_minutes between 15 and 1440
  ),
  weekend_limit_minutes integer check (
    weekend_limit_minutes is null or weekend_limit_minutes between 15 and 1440
  ),
  warning_minutes integer not null default 10 check (
    warning_minutes between 0 and 120
  ),
  schedule_enabled boolean not null default false,
  schedule_windows jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id)
);

alter table public.player_profile_parental_settings enable row level security;
revoke all on public.player_profile_parental_settings from anon, authenticated;

-- Backfill all existing profiles. Existing account-level parental values are
-- copied so users do not silently lose their configured restrictions.
insert into public.player_profile_parental_settings (
  profile_id,
  user_id,
  hide_locked,
  daily_limit_minutes,
  schedule_enabled
)
select
  p.id,
  p.user_id,
  coalesce(pc.hide_locked, false),
  pc.daily_limit_minutes,
  coalesce(pc.schedule_enabled, false)
from public.player_profiles p
left join public.player_parental_controls pc
  on pc.user_id = p.user_id
on conflict (profile_id) do nothing;

-- Preserve the existing account-level age restriction on profiles that have no
-- profile age yet. Primary profiles stay unrestricted.
update public.player_profiles p
set max_age = pc.max_age,
    updated_at = now()
from public.player_parental_controls pc
where p.user_id = pc.user_id
  and p.is_primary = false
  and p.max_age is null
  and pc.max_age is not null;

create or replace function public.player_get_parental_settings()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_row public.player_parental_controls%rowtype;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  insert into public.player_parental_controls (user_id)
  values (v_user_id)
  on conflict (user_id) do nothing;

  select *
  into v_row
  from public.player_parental_controls
  where user_id = v_user_id;

  return jsonb_build_object(
    'has_pin', v_row.pin_hash is not null,
    'enabled', v_row.enabled,
    'blocked_until', v_row.blocked_until
  );
end;
$$;

create or replace function public.player_get_profile_parental_settings(
  p_profile_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_profile public.player_profiles%rowtype;
  v_settings public.player_profile_parental_settings%rowtype;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select *
  into v_profile
  from public.player_profiles
  where id = p_profile_id
    and user_id = v_user_id;

  if not found then
    raise exception 'profile_not_found';
  end if;

  insert into public.player_profile_parental_settings (profile_id, user_id)
  values (v_profile.id, v_user_id)
  on conflict (profile_id) do nothing;

  select *
  into v_settings
  from public.player_profile_parental_settings
  where profile_id = v_profile.id
    and user_id = v_user_id;

  return jsonb_build_object(
    'profile_id', v_profile.id,
    'profile_name', v_profile.name,
    'profile_type', v_profile.profile_type,
    'is_primary', v_profile.is_primary,
    'max_age', v_profile.max_age,
    'hide_locked', v_settings.hide_locked,
    'daily_limit_minutes', v_settings.daily_limit_minutes,
    'weekend_limit_minutes', v_settings.weekend_limit_minutes,
    'warning_minutes', v_settings.warning_minutes,
    'schedule_enabled', v_settings.schedule_enabled,
    'schedule_windows', v_settings.schedule_windows
  );
end;
$$;

create or replace function public.player_update_profile_parental_settings(
  p_profile_id uuid,
  p_pin text,
  p_max_age integer,
  p_hide_locked boolean,
  p_daily_limit_minutes integer,
  p_weekend_limit_minutes integer,
  p_warning_minutes integer,
  p_schedule_enabled boolean,
  p_schedule_windows jsonb
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
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select *
  into v_profile
  from public.player_profiles
  where id = p_profile_id
    and user_id = v_user_id
  for update;

  if not found then
    return jsonb_build_object('success', false, 'reason', 'profile_not_found');
  end if;

  if p_max_age is not null and p_max_age not in (7, 10, 12, 16, 18) then
    return jsonb_build_object('success', false, 'reason', 'invalid_age');
  end if;

  if v_profile.is_primary and p_max_age is not null then
    return jsonb_build_object('success', false, 'reason', 'primary_unrestricted');
  end if;

  if p_daily_limit_minutes is not null
     and (p_daily_limit_minutes < 15 or p_daily_limit_minutes > 1440) then
    return jsonb_build_object('success', false, 'reason', 'invalid_limit');
  end if;

  if p_weekend_limit_minutes is not null
     and (p_weekend_limit_minutes < 15 or p_weekend_limit_minutes > 1440) then
    return jsonb_build_object('success', false, 'reason', 'invalid_limit');
  end if;

  if p_warning_minutes is null or p_warning_minutes < 0 or p_warning_minutes > 120 then
    return jsonb_build_object('success', false, 'reason', 'invalid_warning');
  end if;

  if p_schedule_windows is null or jsonb_typeof(p_schedule_windows) <> 'array' then
    return jsonb_build_object('success', false, 'reason', 'invalid_schedule');
  end if;

  select *
  into v_control
  from public.player_parental_controls
  where user_id = v_user_id
  for update;

  if not found or v_control.pin_hash is null then
    return jsonb_build_object('success', false, 'reason', 'pin_not_configured');
  end if;

  if v_control.blocked_until is not null and v_control.blocked_until > v_now then
    return jsonb_build_object(
      'success', false,
      'reason', 'blocked',
      'blocked_until', v_control.blocked_until
    );
  end if;

  if p_pin !~ '^[0-9]{4}$'
     or extensions.crypt(p_pin, v_control.pin_hash) <> v_control.pin_hash then
    return jsonb_build_object('success', false, 'reason', 'pin_invalid');
  end if;

  update public.player_profiles
  set max_age = case when is_primary then null else p_max_age end,
      updated_at = v_now
  where id = v_profile.id
    and user_id = v_user_id;

  insert into public.player_profile_parental_settings (
    profile_id,
    user_id,
    hide_locked,
    daily_limit_minutes,
    weekend_limit_minutes,
    warning_minutes,
    schedule_enabled,
    schedule_windows,
    updated_at
  )
  values (
    v_profile.id,
    v_user_id,
    p_hide_locked,
    p_daily_limit_minutes,
    p_weekend_limit_minutes,
    p_warning_minutes,
    p_schedule_enabled,
    p_schedule_windows,
    v_now
  )
  on conflict (profile_id) do update
  set hide_locked = excluded.hide_locked,
      daily_limit_minutes = excluded.daily_limit_minutes,
      weekend_limit_minutes = excluded.weekend_limit_minutes,
      warning_minutes = excluded.warning_minutes,
      schedule_enabled = excluded.schedule_enabled,
      schedule_windows = excluded.schedule_windows,
      updated_at = excluded.updated_at;

  return jsonb_build_object('success', true);
end;
$$;

create or replace function public.player_set_parental_enabled(
  p_pin text,
  p_enabled boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_control public.player_parental_controls%rowtype;
  v_now timestamptz := now();
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select *
  into v_control
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

  update public.player_parental_controls
  set enabled = p_enabled,
      updated_at = v_now
  where user_id = v_user_id;

  return jsonb_build_object('success', true);
end;
$$;

revoke all on function public.player_get_profile_parental_settings(uuid) from public;
revoke all on function public.player_update_profile_parental_settings(
  uuid, text, integer, boolean, integer, integer, integer, boolean, jsonb
) from public;
revoke all on function public.player_set_parental_enabled(text, boolean) from public;

grant execute on function public.player_get_profile_parental_settings(uuid) to authenticated;
grant execute on function public.player_update_profile_parental_settings(
  uuid, text, integer, boolean, integer, integer, integer, boolean, jsonb
) to authenticated;
grant execute on function public.player_set_parental_enabled(text, boolean) to authenticated;
