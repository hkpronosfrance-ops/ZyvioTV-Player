-- player_devices : privilèges de table pour le rôle authenticated (403 / 42501).
--
-- Recette Pixel 7 du 09/10/2026 (après #211) :
--   supabase operation=player_devices response=403 sqlstate=42501 pg=missing-table-grant
-- La table, la RLS et les 4 politiques « propriétaire » existent déjà
-- (20261005062419, 20261005062445), mais aucun GRANT n'a été donné : le rôle
-- authenticated est refusé avant même l'évaluation de la RLS.
--
-- Périmètre volontairement minimal :
--   * seul le rôle authenticated reçoit des droits, sur cette seule table ;
--   * anon ne reçoit rien (révoqué explicitement) ;
--   * la RLS reste active et chaque ligne reste limitée à auth.uid() = user_id ;
--   * les quatre droits correspondent aux appels de l'app : upsert
--     (on_conflict=user_id,device_uid → SELECT + INSERT + UPDATE), liste et
--     suppression d'un appareil (DELETE).
--
-- NE PAS APPLIQUER en production sans validation explicite du propriétaire.

alter table public.player_devices enable row level security;

revoke all on table public.player_devices from anon;
grant select, insert, update, delete on table public.player_devices to authenticated;
