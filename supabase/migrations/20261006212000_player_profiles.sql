create table if not exists public.player_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 1 and 40),
  avatar_key text not null default 'avatar_01',
  profile_type text not null default 'standard'
    check (profile_type in ('standard','child')),
  max_age integer check (max_age is null or max_age in (0,7,10,12,16,18)),
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists player_profiles_user_idx
  on public.player_profiles(user_id, created_at asc);

create unique index if not exists player_profiles_one_primary_per_user
  on public.player_profiles(user_id)
  where is_primary = true;

alter table public.player_profiles enable row level security;

drop policy if exists "player_profiles_select_own" on public.player_profiles;
create policy "player_profiles_select_own"
  on public.player_profiles for select to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists "player_profiles_insert_own" on public.player_profiles;
create policy "player_profiles_insert_own"
  on public.player_profiles for insert to authenticated
  with check ((select auth.uid()) = user_id);

drop policy if exists "player_profiles_update_own" on public.player_profiles;
create policy "player_profiles_update_own"
  on public.player_profiles for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "player_profiles_delete_own" on public.player_profiles;
create policy "player_profiles_delete_own"
  on public.player_profiles for delete to authenticated
  using ((select auth.uid()) = user_id);

create or replace function public.player_enforce_profile_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  select count(*)
  into v_count
  from public.player_profiles
  where user_id = new.user_id;

  if tg_op = 'INSERT' and v_count >= 5 then
    raise exception 'profile_limit_reached';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_player_profile_limit on public.player_profiles;
create trigger trg_player_profile_limit
before insert on public.player_profiles
for each row execute function public.player_enforce_profile_limit();

create or replace function public.player_ensure_primary_profile()
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_profile_id uuid;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select id
  into v_profile_id
  from public.player_profiles
  where user_id = v_user_id and is_primary = true
  limit 1;

  if v_profile_id is not null then
    return v_profile_id;
  end if;

  insert into public.player_profiles (
    user_id,
    name,
    avatar_key,
    profile_type,
    is_primary
  )
  values (
    v_user_id,
    'Profil principal',
    'avatar_01',
    'standard',
    true
  )
  returning id into v_profile_id;

  return v_profile_id;
end;
$$;

revoke all on function public.player_ensure_primary_profile() from public;
grant execute on function public.player_ensure_primary_profile() to authenticated;
