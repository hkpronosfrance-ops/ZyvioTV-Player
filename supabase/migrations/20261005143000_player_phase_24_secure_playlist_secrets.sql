-- Phase 24: secure cross-device provider secret synchronization.
-- Provider secrets are stored inside Supabase Vault. Client roles never receive
-- direct access to Vault or the mapping table; authenticated users go through
-- ownership-checking SECURITY DEFINER RPCs only.

create table if not exists public.player_playlist_secrets (
  playlist_id uuid primary key references public.player_playlists(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  vault_secret_id uuid not null,
  secret_version integer not null default 1 check (secret_version > 0),
  updated_at timestamptz not null default now(),
  unique (user_id, playlist_id)
);

create index if not exists player_playlist_secrets_user_idx
  on public.player_playlist_secrets (user_id, updated_at desc);

alter table public.player_playlist_secrets enable row level security;

-- Intentionally no RLS policies: direct client access is denied.
revoke all on table public.player_playlist_secrets from anon, authenticated;

create or replace function public.player_set_playlist_secret(
  p_playlist_id uuid,
  p_secret jsonb
)
returns void
language plpgsql
security definer
set search_path = public, vault
as $$
declare
  v_user_id uuid := auth.uid();
  v_existing_secret_id uuid;
  v_new_secret_id uuid;
  v_secret_text text;
begin
  if v_user_id is null then
    raise exception 'authentication required';
  end if;

  if p_secret is null or jsonb_typeof(p_secret) <> 'object' then
    raise exception 'invalid secret payload';
  end if;

  v_secret_text := p_secret::text;
  if octet_length(v_secret_text) > 16384 then
    raise exception 'secret payload too large';
  end if;

  if not exists (
    select 1
    from public.player_playlists p
    where p.id = p_playlist_id
      and p.user_id = v_user_id
  ) then
    raise exception 'playlist not found';
  end if;

  select s.vault_secret_id
    into v_existing_secret_id
  from public.player_playlist_secrets s
  where s.playlist_id = p_playlist_id
    and s.user_id = v_user_id;

  if v_existing_secret_id is null then
    select vault.create_secret(
      v_secret_text,
      null,
      'ZYVIOTV encrypted IPTV provider configuration'
    ) into v_new_secret_id;

    insert into public.player_playlist_secrets (
      playlist_id,
      user_id,
      vault_secret_id,
      secret_version,
      updated_at
    ) values (
      p_playlist_id,
      v_user_id,
      v_new_secret_id,
      1,
      now()
    )
    on conflict (playlist_id) do update
      set vault_secret_id = excluded.vault_secret_id,
          user_id = excluded.user_id,
          secret_version = public.player_playlist_secrets.secret_version + 1,
          updated_at = now();
  else
    perform vault.update_secret(v_existing_secret_id, v_secret_text);
    update public.player_playlist_secrets
      set secret_version = secret_version + 1,
          updated_at = now()
    where playlist_id = p_playlist_id
      and user_id = v_user_id;
  end if;

  update public.player_playlists
    set secret_status = 'configured',
        updated_at = now()
  where id = p_playlist_id
    and user_id = v_user_id;
end;
$$;

create or replace function public.player_get_playlist_secret(
  p_playlist_id uuid
)
returns jsonb
language plpgsql
security definer
stable
set search_path = public, vault
as $$
declare
  v_user_id uuid := auth.uid();
  v_secret_id uuid;
  v_secret_text text;
begin
  if v_user_id is null then
    raise exception 'authentication required';
  end if;

  select s.vault_secret_id
    into v_secret_id
  from public.player_playlist_secrets s
  join public.player_playlists p on p.id = s.playlist_id
  where s.playlist_id = p_playlist_id
    and s.user_id = v_user_id
    and p.user_id = v_user_id;

  if v_secret_id is null then
    return null;
  end if;

  select d.decrypted_secret
    into v_secret_text
  from vault.decrypted_secrets d
  where d.id = v_secret_id;

  if v_secret_text is null then
    return null;
  end if;

  return v_secret_text::jsonb;
end;
$$;

create or replace function public.player_delete_playlist_secret(
  p_playlist_id uuid
)
returns void
language plpgsql
security definer
set search_path = public, vault
as $$
declare
  v_user_id uuid := auth.uid();
  v_secret_id uuid;
begin
  if v_user_id is null then
    raise exception 'authentication required';
  end if;

  select s.vault_secret_id
    into v_secret_id
  from public.player_playlist_secrets s
  join public.player_playlists p on p.id = s.playlist_id
  where s.playlist_id = p_playlist_id
    and s.user_id = v_user_id
    and p.user_id = v_user_id;

  if v_secret_id is not null then
    delete from vault.secrets where id = v_secret_id;
    delete from public.player_playlist_secrets
      where playlist_id = p_playlist_id
        and user_id = v_user_id;
  end if;

  update public.player_playlists
    set secret_status = 'not_configured',
        updated_at = now()
  where id = p_playlist_id
    and user_id = v_user_id;
end;
$$;

revoke all on function public.player_set_playlist_secret(uuid, jsonb) from public, anon;
revoke all on function public.player_get_playlist_secret(uuid) from public, anon;
revoke all on function public.player_delete_playlist_secret(uuid) from public, anon;

grant execute on function public.player_set_playlist_secret(uuid, jsonb) to authenticated;
grant execute on function public.player_get_playlist_secret(uuid) to authenticated;
grant execute on function public.player_delete_playlist_secret(uuid) to authenticated;
