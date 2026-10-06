-- Add a server time anchor to parental runtime state so Android can keep
-- enforcing cached schedules without trusting a manually changed device clock.

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
    'server_now_epoch_ms', floor(extract(epoch from now()) * 1000)::bigint,
    'parental_enabled', coalesce(v_control.enabled, false),
    'is_child', v_profile.profile_type = 'child',
    'consumed_seconds', v_used,
    'limit_minutes', v_limit,
    'warning_minutes', coalesce(v_settings.warning_minutes, 10),
    'schedule_enabled', coalesce(v_settings.schedule_enabled, false),
    'schedule_windows', coalesce(v_settings.schedule_windows, '[]'::jsonb),
    'exception_until', v_exception_until,
    'exception_until_epoch_ms',
      case
        when v_exception_until is null then null
        else floor(extract(epoch from v_exception_until) * 1000)::bigint
      end,
    'blocked_by_time',
      coalesce(v_control.enabled, false)
      and v_profile.profile_type = 'child'
      and v_limit is not null
      and v_used >= v_limit * 60
      and v_exception_until is null
  );
end;
$$;

revoke all on function public.player_parental_runtime_state(uuid, text) from public;
grant execute on function public.player_parental_runtime_state(uuid, text) to authenticated;
