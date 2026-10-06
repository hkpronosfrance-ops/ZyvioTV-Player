-- Profile-scoped parental content locks.
-- Adult categories remain an Android policy concern and are always hidden for Child profiles.
-- These columns store explicit user-selected category/content locks.

alter table public.player_profile_parental_settings
  add column if not exists locked_category_keys jsonb not null default '[]'::jsonb,
  add column if not exists locked_content_keys jsonb not null default '[]'::jsonb;

alter table public.player_profile_parental_settings
  drop constraint if exists player_profile_parental_locked_categories_array,
  add constraint player_profile_parental_locked_categories_array
    check (jsonb_typeof(locked_category_keys) = 'array');

alter table public.player_profile_parental_settings
  drop constraint if exists player_profile_parental_locked_content_array,
  add constraint player_profile_parental_locked_content_array
    check (jsonb_typeof(locked_content_keys) = 'array');

create or replace function public.player_get_profile_content_locks(
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
  v_control public.player_parental_controls%rowtype;
  v_settings public.player_profile_parental_settings%rowtype;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select * into v_profile
  from public.player_profiles
  where id = p_profile_id
    and user_id = v_user_id;

  if not found then
    raise exception 'profile_not_found';
  end if;

  select * into v_control
  from public.player_parental_controls
  where user_id = v_user_id;

  select * into v_settings
  from public.player_profile_parental_settings
  where profile_id = p_profile_id
    and user_id = v_user_id;

  return jsonb_build_object(
    'parental_enabled', coalesce(v_control.enabled, false),
    'is_child', v_profile.profile_type = 'child',
    'hide_locked', coalesce(v_settings.hide_locked, false),
    'locked_category_keys', coalesce(v_settings.locked_category_keys, '[]'::jsonb),
    'locked_content_keys', coalesce(v_settings.locked_content_keys, '[]'::jsonb)
  );
end;
$$;

create or replace function public.player_update_profile_content_locks(
  p_profile_id uuid,
  p_pin text,
  p_locked_category_keys jsonb,
  p_locked_content_keys jsonb
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

  select * into v_profile
  from public.player_profiles
  where id = p_profile_id
    and user_id = v_user_id;

  if not found then
    return jsonb_build_object('success', false, 'reason', 'profile_not_found');
  end if;

  if v_profile.profile_type <> 'child' then
    return jsonb_build_object('success', false, 'reason', 'standard_profile');
  end if;

  if p_locked_category_keys is null
     or jsonb_typeof(p_locked_category_keys) <> 'array'
     or jsonb_array_length(p_locked_category_keys) > 500 then
    return jsonb_build_object('success', false, 'reason', 'invalid_categories');
  end if;

  if p_locked_content_keys is null
     or jsonb_typeof(p_locked_content_keys) <> 'array'
     or jsonb_array_length(p_locked_content_keys) > 5000 then
    return jsonb_build_object('success', false, 'reason', 'invalid_content');
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

  update public.player_profile_parental_settings
  set locked_category_keys = p_locked_category_keys,
      locked_content_keys = p_locked_content_keys,
      updated_at = v_now
  where profile_id = p_profile_id
    and user_id = v_user_id;

  return jsonb_build_object('success', true);
end;
$$;

revoke all on function public.player_get_profile_content_locks(uuid) from public;
revoke all on function public.player_update_profile_content_locks(uuid, text, jsonb, jsonb) from public;

grant execute on function public.player_get_profile_content_locks(uuid) to authenticated;
grant execute on function public.player_update_profile_content_locks(uuid, text, jsonb, jsonb) to authenticated;
