create table if not exists public.player_app_release_config (
  platform text primary key check (platform in ('android', 'ios', 'tizen', 'webos')),
  latest_version_code bigint not null check (latest_version_code > 0),
  minimum_version_code bigint not null check (minimum_version_code > 0),
  store_url text,
  updated_at timestamptz not null default now(),
  constraint player_app_release_config_min_le_latest
    check (minimum_version_code <= latest_version_code)
);

alter table public.player_app_release_config enable row level security;

drop policy if exists "authenticated can read player release config"
  on public.player_app_release_config;

create policy "authenticated can read player release config"
on public.player_app_release_config
for select
to authenticated
using (true);

revoke all on table public.player_app_release_config from anon;
grant select on table public.player_app_release_config to authenticated;
