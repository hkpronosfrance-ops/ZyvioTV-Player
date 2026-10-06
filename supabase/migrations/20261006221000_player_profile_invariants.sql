-- Lock profile invariants at the database boundary.
-- The primary profile can be renamed/re-avatared, but it remains primary + standard
-- and can never be deleted.

create or replace function public.player_protect_profile_invariants()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'DELETE' then
    if old.is_primary then
      raise exception 'primary_profile_required';
    end if;
    return old;
  end if;

  if tg_op = 'UPDATE' then
    if new.user_id is distinct from old.user_id then
      raise exception 'profile_owner_immutable';
    end if;

    if new.is_primary is distinct from old.is_primary then
      raise exception 'primary_profile_status_immutable';
    end if;

    if old.is_primary and (
      new.profile_type <> 'standard'
      or new.max_age is not null
    ) then
      raise exception 'primary_profile_must_be_standard';
    end if;

    return new;
  end if;

  return new;
end;
$$;

revoke all on function public.player_protect_profile_invariants() from public;
revoke all on function public.player_protect_profile_invariants() from anon;
revoke all on function public.player_protect_profile_invariants() from authenticated;

drop trigger if exists trg_player_profile_invariants on public.player_profiles;
create trigger trg_player_profile_invariants
before update or delete on public.player_profiles
for each row execute function public.player_protect_profile_invariants();

-- Tighten delete RLS as defense in depth.
drop policy if exists "player_profiles_delete_own" on public.player_profiles;
create policy "player_profiles_delete_non_primary_own"
  on public.player_profiles for delete
  to authenticated
  using (
    (select auth.uid()) = user_id
    and is_primary = false
  );
