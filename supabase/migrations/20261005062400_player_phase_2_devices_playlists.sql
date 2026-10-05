create table if not exists public.player_devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_uid text not null,
  display_name text not null,
  platform text not null check (platform in ('android_phone','android_tablet','android_tv','iphone','ipad','tizen','webos','other')),
  app_version text,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, device_uid)
);

create table if not exists public.player_playlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  provider_type text not null check (provider_type in ('m3u','xtream')),
  server_host text,
  playlist_url_hint text,
  secret_status text not null default 'not_configured'
    check (secret_status in ('not_configured','configured')),
  is_enabled boolean not null default true,
  last_synced_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists player_devices_user_id_idx on public.player_devices(user_id);
create index if not exists player_devices_last_seen_idx on public.player_devices(user_id, last_seen_at desc);
create index if not exists player_playlists_user_id_idx on public.player_playlists(user_id);
create index if not exists player_playlists_updated_idx on public.player_playlists(user_id, updated_at desc);

alter table public.player_devices enable row level security;
alter table public.player_playlists enable row level security;

create policy "player_devices_select_own"
on public.player_devices for select to authenticated
using ((select auth.uid()) = user_id);

create policy "player_devices_insert_own"
on public.player_devices for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "player_devices_update_own"
on public.player_devices for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "player_devices_delete_own"
on public.player_devices for delete to authenticated
using ((select auth.uid()) = user_id);

create policy "player_playlists_select_own"
on public.player_playlists for select to authenticated
using ((select auth.uid()) = user_id);

create policy "player_playlists_insert_own"
on public.player_playlists for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "player_playlists_update_own"
on public.player_playlists for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "player_playlists_delete_own"
on public.player_playlists for delete to authenticated
using ((select auth.uid()) = user_id);
