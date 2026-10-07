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


  async function currentUser(session) {
    return window.ZyvioAuth.getUser(session);
  }

  async function ensurePrimaryProfile(session) {
    const profileId = await request(
      "/rest/v1/rpc/player_ensure_primary_profile",
      session,
      { method: "POST", body: {} }
    );
    return String(profileId || "").replace(/^"|"$/g, "");
  }

  async function listProfiles(session) {
    const rows = await request(
      "/rest/v1/player_profiles" +
      "?select=id,name,avatar_key,profile_type,max_age,is_primary" +
      "&order=is_primary.desc,created_at.asc",
      session
    );
    return Array.isArray(rows) ? rows : [];
  }

  async function restorePrimaryProfile(session) {
    await ensurePrimaryProfile(session);
    const profiles = await listProfiles(session);
    return profiles.find((item) => item.is_primary) || profiles[0] || null;
  }

  async function listFavorites(session, profileId) {
    const rows = await request(
      "/rest/v1/player_favorites" +
      "?profile_id=eq." + encodeURIComponent(profileId) +
      "&select=playlist_id,content_type,content_id,title,artwork_url" +
      "&order=updated_at.desc",
      session
    );
    return Array.isArray(rows) ? rows : [];
  }

  async function upsertFavorite(session, profileId, favorite) {
    const user = await currentUser(session);
    const body = [{
      user_id: user.id,
      profile_id: profileId,
      playlist_id: favorite.playlistId,
      content_type: favorite.contentType,
      content_id: favorite.contentId,
      title: favorite.title,
      artwork_url: favorite.artworkUrl || null,
      updated_at: new Date().toISOString(),
    }];

    await request(
      "/rest/v1/player_favorites" +
      "?on_conflict=user_id,profile_id,playlist_id,content_type,content_id",
      session,
      {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body,
      }
    );
  }

  async function removeFavorite(session, profileId, favorite) {
    await request(
      "/rest/v1/player_favorites" +
      "?profile_id=eq." + encodeURIComponent(profileId) +
      "&playlist_id=eq." + encodeURIComponent(favorite.playlistId) +
      "&content_type=eq." + encodeURIComponent(favorite.contentType) +
      "&content_id=eq." + encodeURIComponent(favorite.contentId),
      session,
      { method: "DELETE" }
    );
  }

  async function listWatchProgress(session, profileId, limit = 100) {
    const safeLimit = Math.min(Math.max(Number(limit || 100), 1), 200);
    const rows = await request(
      "/rest/v1/player_watch_progress" +
      "?profile_id=eq." + encodeURIComponent(profileId) +
      "&select=playlist_id,content_type,content_id,title,series_id,season_number,episode_number,artwork_url,position_ms,duration_ms,completed,last_watched_at" +
      "&order=last_watched_at.desc&limit=" + safeLimit,
      session
    );
    return Array.isArray(rows) ? rows : [];
  }

  async function upsertWatchProgress(session, profileId, progress) {
    const user = await currentUser(session);
    const now = new Date().toISOString();
    const body = [{
      user_id: user.id,
      profile_id: profileId,
      playlist_id: progress.playlistId,
      content_type: progress.contentType,
      content_id: progress.contentId,
      title: progress.title,
      series_id: progress.seriesId || null,
      season_number: progress.seasonNumber ?? null,
      episode_number: progress.episodeNumber ?? null,
      artwork_url: progress.artworkUrl || null,
      position_ms: Math.max(0, Number(progress.positionMs || 0)),
      duration_ms: progress.durationMs ? Math.max(0, Number(progress.durationMs)) : null,
      completed: Boolean(progress.completed),
      last_watched_at: now,
      updated_at: now,
    }];

    await request(
      "/rest/v1/player_watch_progress" +
      "?on_conflict=user_id,profile_id,playlist_id,content_type,content_id",
      session,
      {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body,
      }
    );
  }



  async function listLiveHistory(session, profileId, limit = 50) {
    const safeLimit = Math.min(Math.max(Number(limit || 50), 1), 100);
    const rows = await request(
      "/rest/v1/player_live_history" +
      "?profile_id=eq." + encodeURIComponent(profileId) +
      "&select=playlist_id,channel_id,channel_name,logo_url,last_watched_at" +
      "&order=last_watched_at.desc&limit=" + safeLimit,
      session
    );
    return Array.isArray(rows) ? rows : [];
  }

  async function recordLiveHistory(session, profileId, item) {
    const user = await currentUser(session);
    const now = new Date().toISOString();
    await request(
      "/rest/v1/player_live_history" +
      "?on_conflict=user_id,profile_id,playlist_id,channel_id",
      session,
      {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: [{
          user_id: user.id,
          profile_id: profileId,
          playlist_id: item.playlistId,
          channel_id: item.channelId,
          channel_name: item.channelName,
          logo_url: item.logoUrl || null,
          last_watched_at: now,
          updated_at: now,
        }],
      }
    );
  }

  async function getParentalSettings(session) {
    return request("/rest/v1/rpc/player_get_parental_settings", session, {
      method: "POST",
      body: {},
    });
  }

  async function verifyParentalPin(session, pin) {
    return request("/rest/v1/rpc/player_verify_parental_pin", session, {
      method: "POST",
      body: { p_pin: String(pin || "") },
    });
  }

  async function getProfileContentLocks(session, profileId) {
    return request("/rest/v1/rpc/player_get_profile_content_locks", session, {
      method: "POST",
      body: { p_profile_id: profileId },
    });
  }


  async function getParentalRuntimeState(session, profileId, contentKey) {
    return request("/rest/v1/rpc/player_parental_runtime_state", session, {
      method: "POST",
      body: {
        p_profile_id: profileId,
        p_content_key: String(contentKey || ""),
      },
    });
  }

  async function parentalHeartbeat(session, profileId, deviceUid, playing, contentKey, localConsumedSeconds) {
    return request("/rest/v1/rpc/player_parental_screen_time_heartbeat_v2", session, {
      method: "POST",
      body: {
        p_profile_id: profileId,
        p_device_uid: String(deviceUid || ""),
        p_playing: Boolean(playing),
        p_content_key: String(contentKey || ""),
        p_local_consumed_seconds: Math.max(0, Number(localConsumedSeconds || 0)),
      },
    });
  }

  async function grantParentalException(session, profileId, pin, contentKey) {
    return request("/rest/v1/rpc/player_parental_grant_exception", session, {
      method: "POST",
      body: {
        p_profile_id: profileId,
        p_pin: String(pin || ""),
        p_content_key: String(contentKey || ""),
      },
    });
  }

  async function endParentalException(session, profileId, contentKey) {
    return request("/rest/v1/rpc/player_parental_end_exception", session, {
      method: "POST",
      body: {
        p_profile_id: profileId,
        p_content_key: String(contentKey || ""),
      },
    });
  }


  async function registerDevice(session, device) {
    const user = await currentUser(session);
    const now = new Date().toISOString();
    await request(
      "/rest/v1/player_devices?on_conflict=user_id,device_uid",
      session,
      {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: [{
          user_id: user.id,
          device_uid: device.deviceUid,
          display_name: device.displayName,
          platform: "tizen",
          app_version: device.appVersion || null,
          last_seen_at: now,
          updated_at: now,
        }],
      }
    );
  }

  async function listDevices(session) {
    const rows = await request(
      "/rest/v1/player_devices" +
      "?select=id,device_uid,display_name,platform,app_version,last_seen_at" +
      "&order=last_seen_at.desc",
      session
    );
    return Array.isArray(rows) ? rows : [];
  }

  async function renameDevice(session, deviceId, displayName) {
    const clean = String(displayName || "").trim();
    if (!clean) throw new Error("Nom d’appareil invalide.");
    await request(
      "/rest/v1/player_devices?id=eq." + encodeURIComponent(deviceId),
      session,
      {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: {
          display_name: clean,
          updated_at: new Date().toISOString(),
        },
      }
    );
  }

  async function deleteDevice(session, deviceId) {
    await request(
      "/rest/v1/player_devices?id=eq." + encodeURIComponent(deviceId),
      session,
      { method: "DELETE" }
    );
  }

  async function getSystemState(session) {
    const [accountRows, serviceRows] = await Promise.all([
      request(
        "/rest/v1/player_account_status?select=status,message&limit=1",
        session
      ).catch(() => []),
      request(
        "/rest/v1/player_service_state" +
        "?platform=eq.tizen&select=blocking,maintenance_message&limit=1",
        session
      ).catch(() => []),
    ]);

    const account = Array.isArray(accountRows) ? accountRows[0] : null;
    if (account?.status === "suspended") {
      return {
        type: "account_suspended",
        message: account.message || null,
        blocking: true,
      };
    }

    const service = Array.isArray(serviceRows) ? serviceRows[0] : null;
    if (service?.blocking) {
      return {
        type: "maintenance_blocking",
        message: service.maintenance_message || null,
        blocking: true,
      };
    }
    if (service?.maintenance_message) {
      return {
        type: "maintenance_planned",
        message: service.maintenance_message,
        blocking: false,
      };
    }
    return { type: "normal", message: null, blocking: false };
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
    if (!secret || typeof secret !== "object") {
      throw new Error("Source fournisseur indisponible.");
    }

    if (secret.provider_type === "xtream") {
      const serverUrl = String(secret.server_url || "").trim();
      const username = String(secret.username || "").trim();
      const password = String(secret.password || "");
      if (!serverUrl || !username || !password) {
        throw new Error("Configuration Xtream incomplète.");
      }
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

    if (!active) {
      return { playlist: null, providerConfig: null };
    }

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

  const api = {
    currentUser,
    ensurePrimaryProfile,
    listProfiles,
    restorePrimaryProfile,
    listFavorites,
    upsertFavorite,
    removeFavorite,
    listWatchProgress,
    upsertWatchProgress,
    listLiveHistory,
    recordLiveHistory,
    getParentalSettings,
    verifyParentalPin,
    getProfileContentLocks,
    getParentalRuntimeState,
    parentalHeartbeat,
    grantParentalException,
    endParentalException,
    registerDevice,
    listDevices,
    renameDevice,
    deleteDevice,
    getSystemState,
    listPlaylists,
    getPlaylistSecret,
    providerConfigFromSecret,
    restorePrimaryProvider,
  };

  window.ZyvioCloud = api;
  if (typeof module !== "undefined") module.exports = api;
})();
