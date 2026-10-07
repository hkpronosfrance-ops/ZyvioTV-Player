(() => {
  "use strict";

  function authHeaders(session) {
    if (!session?.accessToken) throw new Error("Session absente.");
    return {
      apikey: window.ZyvioAuth.constants.publishableKey,
      Authorization: "Bearer " + session.accessToken,
      "Content-Type": "application/json",
    };
  }

  async function request(path, session, options = {}) {
    const response = await fetch(window.ZyvioAuth.constants.supabaseUrl + path, {
      method: options.method || "GET",
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      headers: { ...authHeaders(session), ...(options.headers || {}) },
      body: options.body ? JSON.stringify(options.body) : undefined,
    });

    let payload = null;
    try { payload = await response.json(); } catch (_) {}
    if (!response.ok) throw new Error("Synchronisation du compte impossible.");
    return payload;
  }

  async function listPlaylists(session) {
    const rows = await request(
      "/rest/v1/player_playlists" +
      "?select=id,name,provider_type,secret_status,is_enabled,priority" +
      "&order=priority.asc,updated_at.desc",
      session
    );
    return Array.isArray(rows) ? rows : [];
  }

  async function getPlaylistSecret(session, playlistId) {
    return request("/rest/v1/rpc/player_get_playlist_secret", session, {
      method: "POST",
      body: { p_playlist_id: playlistId },
    });
  }

  function providerConfigFromSecret(secret) {
    if (!secret || typeof secret !== "object") throw new Error("Source fournisseur indisponible.");

    if (secret.provider_type === "xtream") {
      const serverUrl = String(secret.server_url || "").trim();
      const username = String(secret.username || "").trim();
      const password = String(secret.password || "");
      if (!serverUrl || !username || !password) throw new Error("Configuration Xtream incomplète.");
      return { type: "xtream", serverUrl, username, password };
    }

    if (secret.provider_type === "m3u") {
      const url = String(secret.url || "").trim();
      if (!url) throw new Error("Configuration M3U incomplète.");
      return {
        type: "m3u",
        url,
        xmlTvUrl: String(secret.xmltv_url || "").trim() || null,
      };
    }

    throw new Error("Type de fournisseur non pris en charge.");
  }

  async function restorePrimaryProvider(session) {
    const playlists = await listPlaylists(session);
    const active = playlists
      .filter((item) => item.is_enabled && item.secret_status === "configured")
      .sort((a, b) => Number(a.priority || 0) - Number(b.priority || 0))[0];

    if (!active) return { playlist: null, providerConfig: null };

    const secret = await getPlaylistSecret(session, active.id);
    return {
      playlist: {
        id: active.id,
        name: active.name,
        providerType: active.provider_type,
        priority: active.priority,
      },
      providerConfig: providerConfigFromSecret(secret),
    };
  }

  window.ZyvioCloud = {
    listPlaylists,
    getPlaylistSecret,
    providerConfigFromSecret,
    restorePrimaryProvider,
  };
})();