-- Secure parental PIN recovery foundation.
-- A parental PIN may be reset without the old PIN only after a fresh Supabase
-- authentication event (for example the account-email magic link).

create or replace function public.player_reset_parental_pin_after_recent_auth(
  p_new_pin text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_jwt jsonb := auth.jwt();
  v_auth_epoch bigint;
  v_now_epoch bigint := floor(extract(epoch from now()))::bigint;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_new_pin !~ '^[0-9]{4}$' then
    return jsonb_build_object('success', false, 'reason', 'invalid_format');
  end if;

  v_auth_epoch := nullif(v_jwt ->> 'iat', '')::bigint;

  if v_auth_epoch is null or v_now_epoch - v_auth_epoch > 600 then
    return jsonb_build_object('success', false, 'reason', 'reauth_required');
  end if;

  update public.player_parental_controls
  set pin_hash = extensions.crypt(p_new_pin, extensions.gen_salt('bf')),
      failed_attempts = 0,
      blocked_until = null,
      updated_at = now()
  where user_id = v_user_id;

  if not found then
    insert into public.player_parental_controls (
      user_id,
      pin_hash,
      failed_attempts,
      blocked_until,
      enabled
    )
    values (
      v_user_id,
      extensions.crypt(p_new_pin, extensions.gen_salt('bf')),
      0,
      null,
      false
    );
  end if;

  return jsonb_build_object('success', true);
end;
$$;

revoke all on function public.player_reset_parental_pin_after_recent_auth(text) from public;
grant execute on function public.player_reset_parental_pin_after_recent_auth(text) to authenticated;
