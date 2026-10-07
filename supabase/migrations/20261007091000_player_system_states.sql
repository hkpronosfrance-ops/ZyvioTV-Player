create table if not exists public.player_service_state (
  platform text primary key check (platform in ('android', 'ios', 'tizen', 'webos')),
  maintenance_start_at timestamptz,
  maintenance_end_at timestamptz,
  maintenance_message text,
  blocking boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint player_service_state_window
    check (
      maintenance_start_at is null
      or maintenance_end_at is null
      or maintenance_end_at > maintenance_start_at
    )
);

alter table public.player_service_state enable row level security;

drop policy if exists "authenticated can read player service state"
  on public.player_service_state;

create policy "authenticated can read player service state"
on public.player_service_state
for select
to authenticated
using (true);

revoke all on table public.player_service_state from anon;
grant select on table public.player_service_state to authenticated;

create table if not exists public.player_account_status (
  user_id uuid primary key references auth.users(id) on delete cascade,
  status text not null default 'active'
    check (status in ('active', 'suspended')),
  message text,
  updated_at timestamptz not null default now()
);

alter table public.player_account_status enable row level security;

drop policy if exists "users can read own player account status"
  on public.player_account_status;

create policy "users can read own player account status"
on public.player_account_status
for select
to authenticated
using (auth.uid() = user_id);

revoke all on table public.player_account_status from anon;
grant select on table public.player_account_status to authenticated;
