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

  function xtreamMovieStreamUrl(config, streamId, extension = "mp4") {
    const base = trimSlash(config.serverUrl);
    assertHttpsOrHttp(base);
    const safeExtension = /^[a-z0-9]{2,5}$/i.test(extension) ? extension : "mp4";
    return base + "/movie/" + encode(config.username) + "/" + encode(config.password) +
      "/" + encode(streamId) + "." + safeExtension;
  }

  function xtreamSeriesStreamUrl(config, streamId, extension = "mp4") {
    const base = trimSlash(config.serverUrl);
    assertHttpsOrHttp(base);
    const safeExtension = /^[a-z0-9]{2,5}$/i.test(extension) ? extension : "mp4";
    return base + "/series/" + encode(config.username) + "/" + encode(config.password) +
      "/" + encode(streamId) + "." + safeExtension;
  }

  function xtreamSeriesInfoUrl(config, seriesId) {
    return xtreamApiUrl(config, "get_series_info") + "&series_id=" + encode(seriesId);
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


  async function loadXtreamMovies(config) {
    const [categories, streams] = await Promise.all([
      requestJson(xtreamApiUrl(config, "get_vod_categories")),
      requestJson(xtreamApiUrl(config, "get_vod_streams")),
    ]);

    const categoryMap = new Map(
      (Array.isArray(categories) ? categories : []).map((item) => [
        String(item.category_id),
        String(item.category_name || "Sans catégorie"),
      ])
    );

    return (Array.isArray(streams) ? streams : []).map((item) => ({
      id: String(item.stream_id),
      title: String(item.name || "Film"),
      categoryId: String(item.category_id || ""),
      categoryName: categoryMap.get(String(item.category_id || "")) || "Sans catégorie",
      poster: item.stream_icon || null,
      rating: Number(item.rating || 0),
      added: Number(item.added || 0),
      streamUrl: xtreamMovieStreamUrl(
        config,
        item.stream_id,
        String(item.container_extension || "mp4")
      ),
    }));
  }

  async function loadXtreamSeries(config) {
    const [categories, series] = await Promise.all([
      requestJson(xtreamApiUrl(config, "get_series_categories")),
      requestJson(xtreamApiUrl(config, "get_series")),
    ]);

    const categoryMap = new Map(
      (Array.isArray(categories) ? categories : []).map((item) => [
        String(item.category_id),
        String(item.category_name || "Sans catégorie"),
      ])
    );

    return (Array.isArray(series) ? series : []).map((item) => ({
      id: String(item.series_id),
      title: String(item.name || "Série"),
      categoryId: String(item.category_id || ""),
      categoryName: categoryMap.get(String(item.category_id || "")) || "Sans catégorie",
      poster: item.cover || null,
      rating: Number(item.rating || 0),
      added: Number(item.last_modified || item.added || 0),
    }));
  }

  async function loadXtreamSeriesInfo(config, seriesId) {
    const raw = await requestJson(xtreamSeriesInfoUrl(config, seriesId));
    const seasons = Array.isArray(raw?.seasons) ? raw.seasons : [];
    const episodesBySeason = raw?.episodes && typeof raw.episodes === "object"
      ? raw.episodes
      : {};

    const normalizedEpisodes = {};
    Object.entries(episodesBySeason).forEach(([seasonKey, values]) => {
      normalizedEpisodes[seasonKey] = (Array.isArray(values) ? values : []).map((item, index) => {
        const id = String(item?.id || item?.stream_id || "");
        const extension = String(item?.container_extension || "mp4");
        return {
          id,
          season: Number(seasonKey || 0),
          number: Number(item?.episode_num || index + 1),
          title: String(item?.title || ("Épisode " + (index + 1))),
          synopsis: String(item?.info?.plot || ""),
          streamUrl: id ? xtreamSeriesStreamUrl(config, id, extension) : null,
        };
      }).filter((item) => item.id && item.streamUrl);
    });

    return {
      info: raw?.info || {},
      seasons: seasons.map((season) => ({
        seasonNumber: Number(season.season_number || season.season || 0),
        name: String(season.name || ("Saison " + (season.season_number || season.season || ""))),
      })),
      episodesBySeason: normalizedEpisodes,
    };
  }

  async function loadXtreamShortEpg(config, streamId, limit = 10) {
    const url = xtreamApiUrl(config, "get_short_epg") +
      "&stream_id=" + encode(streamId) +
      "&limit=" + encode(limit);
    const raw = await requestJson(url);
    const listings = Array.isArray(raw?.epg_listings) ? raw.epg_listings : [];
    return listings.map((item) => ({
      id: String(item.id || item.epg_id || ""),
      title: String(item.title || "Programme"),
      description: String(item.description || ""),
      start: Number(item.start_timestamp || 0),
      end: Number(item.stop_timestamp || 0),
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

  async function loadMovies(config) {
    if (!config || config.type !== "xtream") return [];
    return loadXtreamMovies(config);
  }

  async function loadSeries(config) {
    if (!config || config.type !== "xtream") return [];
    return loadXtreamSeries(config);
  }

  const api = {
    loadLive,
    loadMovies,
    loadSeries,
    loadXtreamLive,
    loadXtreamMovies,
    loadXtreamSeries,
    loadXtreamSeriesInfo,
    loadXtreamShortEpg,
    loadM3u,
    parseM3u,
    redactUrl,
    xtreamApiUrl,
    xtreamLiveStreamUrl,
    xtreamMovieStreamUrl,
    xtreamSeriesStreamUrl,
    xtreamSeriesInfoUrl,
  };

  window.ZyvioProvider = api;
  if (typeof module !== "undefined") module.exports = api;
})();
