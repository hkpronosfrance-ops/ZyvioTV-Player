create extension if not exists pgcrypto with schema extensions;

create table if not exists public.player_parental_controls (
  user_id uuid primary key references auth.users(id) on delete cascade,
  pin_hash text,
  max_age integer check (max_age is null or max_age in (0, 7, 10, 12, 16, 18)),
  hide_locked boolean not null default false,
  schedule_enabled boolean not null default false,
  daily_limit_minutes integer check (
    daily_limit_minutes is null or daily_limit_minutes between 15 and 1440
  ),
  failed_attempts smallint not null default 0 check (failed_attempts between 0 and 5),
  blocked_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.player_parental_controls enable row level security;

revoke all on public.player_parental_controls from anon, authenticated;

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
    'max_age', v_row.max_age,
    'hide_locked', v_row.hide_locked,
    'schedule_enabled', v_row.schedule_enabled,
    'daily_limit_minutes', v_row.daily_limit_minutes,
    'blocked_until', v_row.blocked_until
  );
end;
$$;

create or replace function public.player_verify_parental_pin(p_pin text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_row public.player_parental_controls%rowtype;
  v_now timestamptz := now();
  v_attempts smallint;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select *
  into v_row
  from public.player_parental_controls
  where user_id = v_user_id
  for update;

  if not found or v_row.pin_hash is null then
    return jsonb_build_object('verified', false, 'reason', 'pin_not_configured');
  end if;

  if v_row.blocked_until is not null and v_row.blocked_until > v_now then
    return jsonb_build_object(
      'verified', false,
      'reason', 'blocked',
      'blocked_until', v_row.blocked_until
    );
  end if;

  if p_pin ~ '^[0-9]{4}$'
     and extensions.crypt(p_pin, v_row.pin_hash) = v_row.pin_hash then
    update public.player_parental_controls
    set failed_attempts = 0,
        blocked_until = null,
        updated_at = v_now
    where user_id = v_user_id;

    return jsonb_build_object('verified', true);
  end if;

  v_attempts := least(v_row.failed_attempts + 1, 5);

  update public.player_parental_controls
  set failed_attempts = case when v_attempts >= 5 then 0 else v_attempts end,
      blocked_until = case
        when v_attempts >= 5 then v_now + interval '5 minutes'
        else null
      end,
      updated_at = v_now
  where user_id = v_user_id;

  return jsonb_build_object(
    'verified', false,
    'reason', case when v_attempts >= 5 then 'blocked' else 'invalid' end,
    'attempts_remaining', case when v_attempts >= 5 then 0 else 5 - v_attempts end,
    'blocked_until', case
      when v_attempts >= 5 then v_now + interval '5 minutes'
      else null
    end
  );
end;
$$;

create or replace function public.player_set_parental_pin(
  p_new_pin text,
  p_current_pin text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_row public.player_parental_controls%rowtype;
  v_now timestamptz := now();
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_new_pin !~ '^[0-9]{4}$' then
    return jsonb_build_object('success', false, 'reason', 'invalid_format');
  end if;

  insert into public.player_parental_controls (user_id)
  values (v_user_id)
  on conflict (user_id) do nothing;

  select *
  into v_row
  from public.player_parental_controls
  where user_id = v_user_id
  for update;

  if v_row.pin_hash is not null then
    if v_row.blocked_until is not null and v_row.blocked_until > v_now then
      return jsonb_build_object(
        'success', false,
        'reason', 'blocked',
        'blocked_until', v_row.blocked_until
      );
    end if;

    if p_current_pin is null
       or p_current_pin !~ '^[0-9]{4}$'
       or extensions.crypt(p_current_pin, v_row.pin_hash) <> v_row.pin_hash then
      return jsonb_build_object('success', false, 'reason', 'current_pin_invalid');
    end if;
  end if;

  update public.player_parental_controls
  set pin_hash = extensions.crypt(p_new_pin, extensions.gen_salt('bf', 10)),
      failed_attempts = 0,
      blocked_until = null,
      updated_at = v_now
  where user_id = v_user_id;

  return jsonb_build_object('success', true);
end;
$$;

create or replace function public.player_update_parental_settings(
  p_pin text,
  p_max_age integer,
  p_hide_locked boolean,
  p_schedule_enabled boolean,
  p_daily_limit_minutes integer
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_row public.player_parental_controls%rowtype;
  v_now timestamptz := now();
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_max_age is not null and p_max_age not in (0, 7, 10, 12, 16, 18) then
    return jsonb_build_object('success', false, 'reason', 'invalid_age');
  end if;

  if p_daily_limit_minutes is not null
     and (p_daily_limit_minutes < 15 or p_daily_limit_minutes > 1440) then
    return jsonb_build_object('success', false, 'reason', 'invalid_limit');
  end if;

  select *
  into v_row
  from public.player_parental_controls
  where user_id = v_user_id
  for update;

  if not found or v_row.pin_hash is null then
    return jsonb_build_object('success', false, 'reason', 'pin_not_configured');
  end if;

  if v_row.blocked_until is not null and v_row.blocked_until > v_now then
    return jsonb_build_object(
      'success', false,
      'reason', 'blocked',
      'blocked_until', v_row.blocked_until
    );
  end if;

  if p_pin !~ '^[0-9]{4}$'
     or extensions.crypt(p_pin, v_row.pin_hash) <> v_row.pin_hash then
    return jsonb_build_object('success', false, 'reason', 'pin_invalid');
  end if;

  update public.player_parental_controls
  set max_age = p_max_age,
      hide_locked = p_hide_locked,
      schedule_enabled = p_schedule_enabled,
      daily_limit_minutes = p_daily_limit_minutes,
      updated_at = v_now
  where user_id = v_user_id;

  return jsonb_build_object('success', true);
end;
$$;

revoke all on function public.player_get_parental_settings() from public;
revoke all on function public.player_verify_parental_pin(text) from public;
revoke all on function public.player_set_parental_pin(text, text) from public;
revoke all on function public.player_update_parental_settings(text, integer, boolean, boolean, integer) from public;

grant execute on function public.player_get_parental_settings() to authenticated;
grant execute on function public.player_verify_parental_pin(text) to authenticated;
grant execute on function public.player_set_parental_pin(text, text) to authenticated;
grant execute on function public.player_update_parental_settings(text, integer, boolean, boolean, integer) to authenticated;
