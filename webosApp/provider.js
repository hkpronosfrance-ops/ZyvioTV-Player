(() => {
  "use strict";

  function trimSlash(value) {
    return String(value || "").trim().replace(/\/+$/, "");
  }

  function encode(value) {
    return encodeURIComponent(String(value || ""));
  }

  function assertHttpsOrHttp(url) {
    const parsed = new URL(url);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
      throw new Error("Adresse fournisseur invalide.");
    }
    return parsed;
  }

  function redactUrl(raw) {
    try {
      const parsed = new URL(raw);
      ["username", "password", "user", "pass", "token", "auth"].forEach((key) => {
        if (parsed.searchParams.has(key)) parsed.searchParams.set(key, "[REDACTED]");
      });
      parsed.username = parsed.username ? "[REDACTED]" : "";
      parsed.password = parsed.password ? "[REDACTED]" : "";
      return parsed.toString()
        .replace(/\/(live|movie|series)\/[^/]+\/[^/]+\//gi, "/$1/[REDACTED]/[REDACTED]/");
    } catch (_) {
      return "[REDACTED]";
    }
  }

  function xtreamApiUrl(config, action) {
    const base = trimSlash(config.serverUrl);
    assertHttpsOrHttp(base);
    return base + "/player_api.php?username=" + encode(config.username) +
      "&password=" + encode(config.password) + "&action=" + encode(action);
  }

  function xtreamLiveStreamUrl(config, streamId, extension = "ts") {
    const base = trimSlash(config.serverUrl);
    assertHttpsOrHttp(base);
    const safeExtension = /^[a-z0-9]{2,5}$/i.test(extension) ? extension : "ts";
    return base + "/live/" + encode(config.username) + "/" + encode(config.password) +
      "/" + encode(streamId) + "." + safeExtension;
  }

  async function requestJson(url, timeoutMs = 15000) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, {
        method: "GET",
        cache: "no-store",
        signal: controller.signal,
        credentials: "omit",
        referrerPolicy: "no-referrer",
      });
      if (!response.ok) throw new Error("Le fournisseur ne répond pas.");
      return await response.json();
    } catch (_) {
      throw new Error("Impossible de charger les données du fournisseur.");
    } finally {
      clearTimeout(timer);
    }
  }

  async function loadXtreamLive(config) {
    const [categories, streams] = await Promise.all([
      requestJson(xtreamApiUrl(config, "get_live_categories")),
      requestJson(xtreamApiUrl(config, "get_live_streams")),
    ]);

    const categoryMap = new Map(
      (Array.isArray(categories) ? categories : []).map((item) => [
        String(item.category_id),
        String(item.category_name || "Sans catégorie"),
      ])
    );

    return (Array.isArray(streams) ? streams : []).map((item) => ({
      id: String(item.stream_id),
      name: String(item.name || "Chaîne"),
      number: Number(item.num || 0),
      categoryId: String(item.category_id || ""),
      categoryName: categoryMap.get(String(item.category_id || "")) || "Sans catégorie",
      logo: item.stream_icon || null,
      epgChannelId: item.epg_channel_id || null,
      streamUrl: xtreamLiveStreamUrl(config, item.stream_id),
    }));
  }

  function parseM3u(text) {
    const lines = String(text || "").split(/\r?\n/);
    const items = [];
    let meta = null;

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) continue;

      if (line.startsWith("#EXTINF:")) {
        const name = line.includes(",") ? line.slice(line.lastIndexOf(",") + 1).trim() : "Chaîne";
        const logo = /tvg-logo="([^"]*)"/i.exec(line)?.[1] || null;
        const group = /group-title="([^"]*)"/i.exec(line)?.[1] || "Sans catégorie";
        const tvgId = /tvg-id="([^"]*)"/i.exec(line)?.[1] || null;
        meta = { name, logo, categoryName: group, epgChannelId: tvgId };
        continue;
      }

      if (!line.startsWith("#") && meta) {
        assertHttpsOrHttp(line);
        items.push({
          id: String(items.length + 1),
          number: items.length + 1,
          ...meta,
          streamUrl: line,
        });
        meta = null;
      }
    }

    return items;
  }

  async function loadM3u(config) {
    const url = String(config.url || "").trim();
    assertHttpsOrHttp(url);
    try {
      const response = await fetch(url, {
        cache: "no-store",
        credentials: "omit",
        referrerPolicy: "no-referrer",
      });
      if (!response.ok) throw new Error();
      return parseM3u(await response.text());
    } catch (_) {
      throw new Error("Impossible de charger la playlist M3U.");
    }
  }

  async function loadLive(config) {
    if (!config || !config.type) throw new Error("Fournisseur non configuré.");
    if (config.type === "xtream") return loadXtreamLive(config);
    if (config.type === "m3u") return loadM3u(config);
    throw new Error("Type de fournisseur non pris en charge.");
  }

  const api = {
    loadLive,
    loadXtreamLive,
    loadM3u,
    parseM3u,
    redactUrl,
    xtreamApiUrl,
    xtreamLiveStreamUrl,
  };

  window.ZyvioProvider = api;
  if (typeof module !== "undefined") module.exports = api;
})();
