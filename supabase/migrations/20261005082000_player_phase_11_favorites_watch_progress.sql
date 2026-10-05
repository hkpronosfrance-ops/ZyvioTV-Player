create table if not exists public.player_favorites (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  playlist_id uuid not null references public.player_playlists(id) on delete cascade,
  content_type text not null check (content_type in ('live','movie','series')),
  content_id text not null,
  title text not null,
  artwork_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, playlist_id, content_type, content_id)
);

create index if not exists player_favorites_user_updated_idx
  on public.player_favorites (user_id, updated_at desc);

alter table public.player_favorites enable row level security;

create policy "player_favorites_select_own"
  on public.player_favorites for select
  using ((select auth.uid()) = user_id);

create policy "player_favorites_insert_own"
  on public.player_favorites for insert
  with check ((select auth.uid()) = user_id);

create policy "player_favorites_update_own"
  on public.player_favorites for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "player_favorites_delete_own"
  on public.player_favorites for delete
  using ((select auth.uid()) = user_id);

create table if not exists public.player_watch_progress (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  playlist_id uuid not null references public.player_playlists(id) on delete cascade,
  content_type text not null check (content_type in ('movie','episode')),
  content_id text not null,
  title text not null,
  series_id text,
  season_number integer,
  episode_number integer,
  artwork_url text,
  position_ms bigint not null default 0 check (position_ms >= 0),
  duration_ms bigint check (duration_ms is null or duration_ms >= 0),
  completed boolean not null default false,
  last_watched_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, playlist_id, content_type, content_id)
);

create index if not exists player_watch_progress_user_watched_idx
  on public.player_watch_progress (user_id, last_watched_at desc);

alter table public.player_watch_progress enable row level security;

create policy "player_watch_progress_select_own"
  on public.player_watch_progress for select
  using ((select auth.uid()) = user_id);

create policy "player_watch_progress_insert_own"
  on public.player_watch_progress for insert
  with check ((select auth.uid()) = user_id);

create policy "player_watch_progress_update_own"
  on public.player_watch_progress for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "player_watch_progress_delete_own"
  on public.player_watch_progress for delete
  using ((select auth.uid()) = user_id);
