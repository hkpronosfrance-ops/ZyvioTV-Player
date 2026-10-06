insert into public.player_profiles (
  user_id,
  name,
  avatar_key,
  profile_type,
  is_primary
)
select distinct
  source.user_id,
  'Profil principal',
  'avatar_01',
  'standard',
  true
from (
  select user_id from public.player_favorites
  union
  select user_id from public.player_watch_progress
) as source
where not exists (
  select 1
  from public.player_profiles p
  where p.user_id = source.user_id
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
  drop constraint if exists player_favorites_user_id_playlist_id_content_type_content_i_key;

alter table public.player_watch_progress
  drop constraint if exists player_watch_progress_user_id_playlist_id_content_type_cont_key;

alter table public.player_favorites
  add constraint player_favorites_profile_content_unique
  unique (user_id, profile_id, playlist_id, content_type, content_id);

alter table public.player_watch_progress
  add constraint player_watch_progress_profile_content_unique
  unique (user_id, profile_id, playlist_id, content_type, content_id);

create index if not exists player_favorites_profile_updated_idx
  on public.player_favorites (user_id, profile_id, updated_at desc);

create index if not exists player_watch_progress_profile_watched_idx
  on public.player_watch_progress (user_id, profile_id, last_watched_at desc);

drop policy if exists "player_favorites_insert_own" on public.player_favorites;
create policy "player_favorites_insert_own"
  on public.player_favorites for insert to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = (select auth.uid())
    )
  );

drop policy if exists "player_favorites_update_own" on public.player_favorites;
create policy "player_favorites_update_own"
  on public.player_favorites for update to authenticated
  using ((select auth.uid()) = user_id)
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = (select auth.uid())
    )
  );

drop policy if exists "player_watch_progress_insert_own" on public.player_watch_progress;
create policy "player_watch_progress_insert_own"
  on public.player_watch_progress for insert to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = (select auth.uid())
    )
  );

drop policy if exists "player_watch_progress_update_own" on public.player_watch_progress;
create policy "player_watch_progress_update_own"
  on public.player_watch_progress for update to authenticated
  using ((select auth.uid()) = user_id)
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.player_profiles p
      where p.id = profile_id
        and p.user_id = (select auth.uid())
    )
  );
