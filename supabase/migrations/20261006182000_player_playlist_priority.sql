alter table public.player_playlists
  add column if not exists priority integer;

with ranked as (
  select
    id,
    row_number() over (
      partition by user_id
      order by updated_at desc, created_at desc, id
    ) as rn
  from public.player_playlists
)
update public.player_playlists as p
set priority = least(ranked.rn, 10)
from ranked
where p.id = ranked.id
  and p.priority is null;

alter table public.player_playlists
  alter column priority set default 1;

update public.player_playlists
set priority = 1
where priority is null;

alter table public.player_playlists
  alter column priority set not null;

alter table public.player_playlists
  drop constraint if exists player_playlists_priority_check;

alter table public.player_playlists
  add constraint player_playlists_priority_check
  check (priority between 1 and 10);

create index if not exists player_playlists_user_priority_idx
  on public.player_playlists (user_id, priority asc, updated_at desc);
