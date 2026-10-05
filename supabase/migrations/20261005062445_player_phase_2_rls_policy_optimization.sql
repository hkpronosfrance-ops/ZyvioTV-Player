drop policy if exists "player_devices_select_own" on public.player_devices;
create policy "player_devices_select_own" on public.player_devices
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "player_devices_insert_own" on public.player_devices;
create policy "player_devices_insert_own" on public.player_devices
for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "player_devices_update_own" on public.player_devices;
create policy "player_devices_update_own" on public.player_devices
for update to authenticated using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "player_devices_delete_own" on public.player_devices;
create policy "player_devices_delete_own" on public.player_devices
for delete to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "player_playlists_select_own" on public.player_playlists;
create policy "player_playlists_select_own" on public.player_playlists
for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "player_playlists_insert_own" on public.player_playlists;
create policy "player_playlists_insert_own" on public.player_playlists
for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "player_playlists_update_own" on public.player_playlists;
create policy "player_playlists_update_own" on public.player_playlists
for update to authenticated using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "player_playlists_delete_own" on public.player_playlists;
create policy "player_playlists_delete_own" on public.player_playlists
for delete to authenticated using ((select auth.uid()) = user_id);
