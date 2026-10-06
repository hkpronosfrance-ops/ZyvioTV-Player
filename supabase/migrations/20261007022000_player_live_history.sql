create table if not exists public.player_live_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.player_profiles(id) on delete cascade,
  playlist_id uuid not null references public.player_playlists(id) on delete cascade,
  channel_id text not null,
  channel_name text not null,
  logo_url text,
  last_watched_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint player_live_history_profile_channel_key
    unique (user_id, profile_id, playlist_id, channel_id)
);

create index if not exists player_live_history_user_profile_watched_idx
  on public.player_live_history (user_id, profile_id, last_watched_at desc);

alter table public.player_live_history enable row level security;

create policy "player_live_history_select_own_profile"
  on public.player_live_history for select
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

create policy "player_live_history_insert_own_profile"
  on public.player_live_history for insert
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

create policy "player_live_history_update_own_profile"
  on public.player_live_history for update
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

create policy "player_live_history_delete_own_profile"
  on public.player_live_history for delete
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
