# Supabase `player_devices` — 403 (42501)

## Constat (Pixel 7 émulateur, 09/10/2026, `main` = `f1e3591`)

```
supabase operation=player_devices response=403 session=fresh authError=permission-denied sqlstate=42501 pg=missing-table-grant retried=false
```

`42501` avec un message « permission denied for table » signifie que le rôle
`authenticated` n'a **aucun privilège de table**. Ce n'est pas un refus RLS
(qui produirait `pg=rls-violation` à l'insertion, ou une liste vide en lecture).

Dans le dépôt, `20261005062419_player_phase_2_devices_playlists.sql` crée la
table, active la RLS et ses politiques propriétaires, mais ne contient aucun
`GRANT`. Le projet Supabase n'expose pas automatiquement les nouvelles tables
au rôle `authenticated` : `player_profiles` et `player_playlists` avaient déjà
dû être exposées une par une.

## Vérification en lecture seule (SQL Editor, avant et après)

```sql
select
  has_table_privilege('authenticated', 'public.player_devices', 'SELECT') as auth_select,
  has_table_privilege('authenticated', 'public.player_devices', 'INSERT') as auth_insert,
  has_table_privilege('authenticated', 'public.player_devices', 'UPDATE') as auth_update,
  has_table_privilege('authenticated', 'public.player_devices', 'DELETE') as auth_delete,
  has_table_privilege('anon', 'public.player_devices', 'SELECT') as anon_select,
  (select relrowsecurity from pg_class where oid = 'public.player_devices'::regclass) as rls_enabled;

select policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public' and tablename = 'player_devices'
order by policyname;
```

Attendu **avant** : `auth_*` = false, `anon_select` = false, `rls_enabled` =
true, 4 politiques `player_devices_*_own` limitées à `authenticated` et à
`auth.uid() = user_id`. Si `rls_enabled` est false ou si une politique est
différente, **ne pas appliquer** la migration : revoir d'abord la RLS.

Attendu **après** : `auth_*` = true, `anon_select` = false, `rls_enabled` = true.

## Correctif proposé

`supabase/migrations/20261009230000_player_devices_authenticated_grant.sql` :
`GRANT select, insert, update, delete` au seul rôle `authenticated`, `REVOKE`
explicite pour `anon`, RLS laissée active. Aucune autre table, fonction ni
politique n'est modifiée.

**Statut : appliqué en production le 09/10/2026 (~22:40 UTC) par le
propriétaire, dans le SQL Editor**, après la vérification « avant » conforme
(`auth_*` = false, `anon_select` = false, `rls_enabled` = true, 4 politiques
`player_devices_*_own` limitées à `authenticated` et `auth.uid() = user_id`).
Les deux instructions exécutées sont exactement celles de la migration
(« Success. No rows returned »). Vérification « après » conforme (`auth_*` = true, `anon_select` = false,
`rls_enabled` = true) et Logcat Pixel 7 émulateur :
`supabase operation=player_devices response=201 … sqlstate=none pg=none`.

## Recette après application

Logcat `ZyvioNetwork` : `supabase operation=player_devices response=200` (ou
`201` pour l'enregistrement de l'appareil), plus aucune ligne `pg=missing-table-grant`.
