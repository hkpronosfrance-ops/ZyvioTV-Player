(() => {
  "use strict";

  const SUPABASE_URL = "https://nvpuftuluguawdxonmlc.supabase.co";
  const PUBLISHABLE_KEY = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC";
  const STORAGE_KEY = "zyviotv.tizen.session.v1";

  function readSession() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const value = JSON.parse(raw);
      if (!value?.accessToken || !value?.refreshToken || !value?.expiresAt) return null;
      return value;
    } catch (_) {
      return null;
    }
  }

  function writeSession(session) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  function clearSession() {
    localStorage.removeItem(STORAGE_KEY);
  }

  async function request(path, options = {}) {
    const response = await fetch(SUPABASE_URL + path, {
      method: options.method || "GET",
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      headers: {
        "Content-Type": "application/json",
        apikey: PUBLISHABLE_KEY,
        Authorization: "Bearer " + (options.token || PUBLISHABLE_KEY),
        ...(options.headers || {}),
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
    });

    let payload = null;
    try { payload = await response.json(); } catch (_) {}

    if (!response.ok) {
      const message = payload?.msg || payload?.message || payload?.error_description ||
        "Connexion impossible.";
      throw new Error(message);
    }
    return payload;
  }

  function normalizeSession(payload) {
    const accessToken = payload?.access_token;
    const refreshToken = payload?.refresh_token;
    if (!accessToken || !refreshToken) {
      throw new Error("Session invalide.");
    }
    const expiresIn = Number(payload?.expires_in || 3600);
    return {
      accessToken,
      refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + expiresIn,
    };
  }

  async function signIn(email, password) {
    const payload = await request("/auth/v1/token?grant_type=password", {
      method: "POST",
      body: {
        email: String(email || "").trim(),
        password: String(password || ""),
      },
    });
    const session = normalizeSession(payload);
    writeSession(session);
    return session;
  }

  async function refresh(session) {
    const payload = await request("/auth/v1/token?grant_type=refresh_token", {
      method: "POST",
      body: { refresh_token: session.refreshToken },
    });
    const next = normalizeSession(payload);
    writeSession(next);
    return next;
  }

  async function getUser(session) {
    return request("/auth/v1/user", { token: session.accessToken });
  }

  async function restoreSession() {
    const stored = readSession();
    if (!stored) return null;

    try {
      const now = Math.floor(Date.now() / 1000);
      const session = stored.expiresAt > now + 60 ? stored : await refresh(stored);
      await getUser(session);
      return session;
    } catch (_) {
      clearSession();
      return null;
    }
  }

  async function signOut() {
    const session = readSession();
    if (session) {
      try {
        await request("/auth/v1/logout", {
          method: "POST",
          token: session.accessToken,
          body: {},
        });
      } catch (_) {}
    }
    clearSession();
  }

  const api = {
    signIn,
    signOut,
    restoreSession,
    getUser,
    readSession,
    clearSession,
    constants: Object.freeze({
      supabaseUrl: SUPABASE_URL,
      publishableKey: PUBLISHABLE_KEY,
    }),
  };

  window.ZyvioAuth = api;
  if (typeof module !== "undefined") module.exports = api;
})();
