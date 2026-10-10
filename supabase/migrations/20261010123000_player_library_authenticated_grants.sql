-- Bibliothèque (favoris, progression, chaînes récentes) : privilèges de table
-- pour le rôle authenticated (HTTP 403).
--
-- Recette Pixel 7 émulateur du 10/10/2026 (variante perf, après #217) : l'Accueil
-- affichait « Impossible de charger vos favoris ». Journaux Supabase : GET
-- player_favorites, player_watch_progress et player_live_history → 403.
-- Audit en lecture seule : RLS active et quatre politiques « propriétaire du
-- profil » par table (auth.uid() = user_id et profil appartenant à l'utilisateur),
-- mais aucun privilège SELECT/INSERT/UPDATE/DELETE pour authenticated : même
-- cause que player_devices (20261009230000).
--
-- Périmètre volontairement minimal :
--   * seul le rôle authenticated reçoit des droits, sur ces trois tables ;
--   * anon ne reçoit rien (révoqué explicitement) ;
--   * la RLS reste active ; chaque ligne reste limitée aux profils de l'utilisateur.
--
-- Appliquée en production le 10/10/2026 par le propriétaire (SQL Editor),
-- vérifiée ensuite en lecture seule (privilèges et RLS).
-- Idempotente : peut être rejouée sans effet de bord.

alter table public.player_favorites enable row level security;
alter table public.player_watch_progress enable row level security;
alter table public.player_live_history enable row level security;

revoke all on table public.player_favorites from anon;
revoke all on table public.player_watch_progress from anon;
revoke all on table public.player_live_history from anon;

grant select, insert, update, delete on table public.player_favorites to authenticated;
grant select, insert, update, delete on table public.player_watch_progress to authenticated;
grant select, insert, update, delete on table public.player_live_history to authenticated;
