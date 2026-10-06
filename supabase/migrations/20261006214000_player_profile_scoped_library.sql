-- Scope Favorites / Continue Watching / History / progress to a ZYVIOTV profile.
-- Existing library rows are preserved and assigned to the account primary profile.

-- Ensure every existing account has a primary profile before backfilling library rows.
insert into public.player_profiles (
  user_id,
  name,
  avatar_key,
  profile_type,
  is_primary
)
select
  u.id,
  'Profil principal',
  'avatar_01',
  'standard',
  true
from auth.users u
where not exists (
  select 1
  from public.player_profiles p
  where p.user_id = u.id
    and p.is_primary = true
);

alter table public.player_favorites
  add column if not exists profile_id uuid references public.player_profiles(id) on delete cascade;

alter table public.player_watch_progress
  add column if not exists profile_id uuid references public.player_profiles(id) on delete cascade;

update public.player_favorites f
set profile_id = p.id
from public.player_profiles p
where f.profile_id is null
  and p.user_id = f.user_id
  and p.is_primary = true;

update public.player_watch_progress w
set profile_id = p.id
from public.player_profiles p
where w.profile_id is null
  and p.user_id = w.user_id
  and p.is_primary = true;

alter table public.player_favorites
  alter column profile_id set not null;

alter table public.player_watch_progress
  alter column profile_id set not null;

alter table public.player_favorites
  drop constraint if exists player_favorites_user_id_playlist_id_content_type_content_id_key;

alter table public.player_watch_progress
  drop constraint if exists player_watch_progress_user_id_playlist_id_content_type_content_id_key;

alter table public.player_favorites
  add constraint player_favorites_profile_content_key
  unique (user_id, profile_id, playlist_id, content_type, content_id);

alter table public.player_watch_progress
  add constraint player_watch_progress_profile_content_key
  unique (user_id, profile_id, playlist_id, content_type, content_id);

drop index if exists public.player_favorites_user_updated_idx;
create index player_favorites_user_profile_updated_idx
  on public.player_favorites (user_id, profile_id, updated_at desc);

drop index if exists public.player_watch_progress_user_watched_idx;
create index player_watch_progress_user_profile_watched_idx
  on public.player_watch_progress (user_id, profile_id, last_watched_at desc);

drop policy if exists "player_favorites_select_own" on public.player_favorites;
drop policy if exists "player_favorites_insert_own" on public.player_favorites;
drop policy if exists "player_favorites_update_own" on public.player_favorites;
drop policy if exists "player_favorites_delete_own" on public.player_favorites;

create policy "player_favorites_select_own_profile"
  on public.player_favorites for select
  to authenticated
  using (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

create policy "player_favorites_insert_own_profile"
  on public.player_favorites for insert
  to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

create policy "player_favorites_update_own_profile"
  on public.player_favorites for update
  to authenticated
  using (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  )
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

create policy "player_favorites_delete_own_profile"
  on public.player_favorites for delete
  to authenticated
  using (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

drop policy if exists "player_watch_progress_select_own" on public.player_watch_progress;
drop policy if exists "player_watch_progress_insert_own" on public.player_watch_progress;
drop policy if exists "player_watch_progress_update_own" on public.player_watch_progress;
drop policy if exists "player_watch_progress_delete_own" on public.player_watch_progress;

create policy "player_watch_progress_select_own_profile"
  on public.player_watch_progress for select
  to authenticated
  using (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

create policy "player_watch_progress_insert_own_profile"
  on public.player_watch_progress for insert
  to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

create policy "player_watch_progress_update_own_profile"
  on public.player_watch_progress for update
  to authenticated
  using (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  )
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

create policy "player_watch_progress_delete_own_profile"
  on public.player_watch_progress for delete
  to authenticated
  using (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = user_id
        and p.user_id = (select auth.uid())
    )
  );

-- Trigger functions are internal implementation details, not client RPCs.
revoke all on function public.player_enforce_profile_limit() from public;
revoke all on function public.player_enforce_profile_limit() from anon;
revoke all on function public.player_enforce_profile_limit() from authenticated;
