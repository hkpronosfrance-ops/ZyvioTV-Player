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

  function stableId(value) {
    const text = String(value || "");
    let hash = 2166136261;
    for (let i = 0; i < text.length; i += 1) {
      hash ^= text.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0).toString(36);
  }

  function normalizeLabel(value) {
    return String(value || "")
      .toLowerCase()
      .normalize?.("NFD")
      .replace?.(/[\u0300-\u036f]/g, "") || String(value || "").toLowerCase();
  }

  function parseEpisodeIdentity(name) {
    const original = String(name || "").trim();
    const patterns = [
      /^(.*?)[\s._-]+s(\d{1,2})[\s._-]*e(\d{1,3})(?:\b|[\s._-])(.*)$/i,
      /^(.*?)[\s._-]+(\d{1,2})x(\d{1,3})(?:\b|[\s._-])(.*)$/i,
      /^(.*?)[\s._-]+saison[\s._-]*(\d{1,2})[\s._-]+(?:episode|ep)[\s._-]*(\d{1,3})(?:\b|[\s._-])(.*)$/i,
    ];

    for (const pattern of patterns) {
      const match = pattern.exec(original);
      if (!match) continue;
      const seriesTitle = String(match[1] || "").replace(/[._]+/g, " ").replace(/\s+/g, " ").trim();
      if (!seriesTitle) continue;
      return {
        seriesTitle,
        season: Number(match[2] || 0),
        episode: Number(match[3] || 0),
        episodeTitle: String(match[4] || "").replace(/^[\s._-]+/, "").replace(/[._]+/g, " ").trim(),
      };
    }
    return null;
  }

  function classifyM3uEntry(entry) {
    const group = normalizeLabel(entry.categoryName);
    const name = normalizeLabel(entry.name);
    const episode = parseEpisodeIdentity(entry.name);

    if (episode) return { type: "episode", episode };

    const seriesGroup = /(^|\b)(series|serie|tv shows?|episodes?|saisons?)(\b|$)/i.test(group);
    if (seriesGroup) {
      return {
        type: "episode",
        episode: {
          seriesTitle: String(entry.name || "Série").trim(),
          season: 1,
          episode: 1,
          episodeTitle: "",
        },
      };
    }

    const movieGroup = /(^|\b)(vod|movies?|films?|cinema|cine)(\b|$)/i.test(group);
    const liveGroup = /(^|\b)(live|tv|chaines?|channels?|sports?|news|infos?|radio)(\b|$)/i.test(group);

    if (movieGroup) return { type: "movie" };
    if (liveGroup) return { type: "live" };

    const pathname = (() => {
      try { return new URL(entry.streamUrl).pathname.toLowerCase(); } catch (_) { return ""; }
    })();

    if (/\/(movie|vod)\//.test(pathname)) return { type: "movie" };
    if (/\/(series)\//.test(pathname)) {
      return {
        type: "episode",
        episode: episode || {
          seriesTitle: String(entry.name || "Série").trim(),
          season: 1,
          episode: 1,
          episodeTitle: "",
        },
      };
    }
    if (/\/(live)\//.test(pathname)) return { type: "live" };

    if (/\.(mp4|mkv|avi|mov|m4v|webm)(?:$|\?)/i.test(entry.streamUrl)) {
      return { type: "movie" };
    }

    // M3U defaults to Live only when metadata gives no reliable VOD/Series signal.
    return { type: "live" };
  }

  function parseM3u(text) {
    const lines = String(text || "").split(/\r?\n/);
    const items = [];
    let meta = null;

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) continue;

      if (line.startsWith("#EXTINF:")) {
        const name = line.includes(",") ? line.slice(line.lastIndexOf(",") + 1).trim() : "Contenu";
        const logo = /tvg-logo="([^"]*)"/i.exec(line)?.[1] || null;
        const group = /group-title="([^"]*)"/i.exec(line)?.[1] || "Sans catégorie";
        const tvgId = /tvg-id="([^"]*)"/i.exec(line)?.[1] || null;
        const tvgName = /tvg-name="([^"]*)"/i.exec(line)?.[1] || null;
        meta = { name, logo, categoryName: group, epgChannelId: tvgId, tvgName };
        continue;
      }

      if (!line.startsWith("#") && meta) {
        assertHttpsOrHttp(line);
        const id = "m3u-" + stableId([meta.epgChannelId, meta.name, meta.categoryName, line].join("|"));
        items.push({
          id,
          number: items.length + 1,
          ...meta,
          streamUrl: line,
        });
        meta = null;
      }
    }

    return items;
  }

  const m3uCache = new Map();

  async function loadM3u(config) {
    const url = String(config.url || "").trim();
    assertHttpsOrHttp(url);

    if (m3uCache.has(url)) return m3uCache.get(url);

    const promise = (async () => {
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
    })();

    m3uCache.set(url, promise);
    try {
      return await promise;
    } catch (error) {
      m3uCache.delete(url);
      throw error;
    }
  }

  async function loadM3uCatalog(config) {
    const entries = await loadM3u(config);
    const live = [];
    const movies = [];
    const seriesMap = new Map();

    entries.forEach((entry) => {
      const classification = classifyM3uEntry(entry);

      if (classification.type === "live") {
        live.push({
          id: entry.id,
          name: entry.name,
          number: entry.number,
          categoryId: "m3u-group-" + stableId(entry.categoryName),
          categoryName: entry.categoryName,
          logo: entry.logo,
          epgChannelId: entry.epgChannelId,
          streamUrl: entry.streamUrl,
        });
        return;
      }

      if (classification.type === "movie") {
        movies.push({
          id: entry.id,
          title: entry.name,
          categoryId: "m3u-group-" + stableId(entry.categoryName),
          categoryName: entry.categoryName,
          poster: entry.logo,
          rating: 0,
          added: 0,
          streamUrl: entry.streamUrl,
        });
        return;
      }

      const episode = classification.episode;
      const seriesKey = normalizeLabel(episode.seriesTitle);
      let series = seriesMap.get(seriesKey);
      if (!series) {
        series = {
          id: "m3u-series-" + stableId(seriesKey + "|" + entry.categoryName),
          title: episode.seriesTitle,
          categoryId: "m3u-group-" + stableId(entry.categoryName),
          categoryName: entry.categoryName,
          poster: entry.logo,
          rating: 0,
          added: 0,
          episodesBySeason: {},
        };
        seriesMap.set(seriesKey, series);
      }

      const seasonKey = String(Math.max(1, Number(episode.season || 1)));
      if (!series.episodesBySeason[seasonKey]) series.episodesBySeason[seasonKey] = [];
      series.episodesBySeason[seasonKey].push({
        id: entry.id,
        season: Number(seasonKey),
        number: Math.max(1, Number(episode.episode || series.episodesBySeason[seasonKey].length + 1)),
        title: episode.episodeTitle || ("Épisode " + Math.max(1, Number(episode.episode || 1))),
        synopsis: "",
        streamUrl: entry.streamUrl,
      });
    });

    const series = Array.from(seriesMap.values()).map((item) => ({
      ...item,
      episodesBySeason: Object.fromEntries(
        Object.entries(item.episodesBySeason).map(([season, values]) => [
          season,
          values.sort((a, b) => a.number - b.number),
        ])
      ),
    }));

    return { live, movies, series };
  }

  async function loadM3uSeriesInfo(config, seriesId) {
    const catalog = await loadM3uCatalog(config);
    const series = catalog.series.find((item) => item.id === String(seriesId));
    if (!series) throw new Error("Série M3U introuvable.");

    return {
      info: {
        name: series.title,
        cover: series.poster || null,
      },
      seasons: Object.keys(series.episodesBySeason)
        .map(Number)
        .sort((a, b) => a - b)
        .map((seasonNumber) => ({
          seasonNumber,
          name: "Saison " + seasonNumber,
        })),
      episodesBySeason: series.episodesBySeason,
    };
  }

  async function loadLive(config) {
    if (!config || !config.type) throw new Error("Fournisseur non configuré.");
    if (config.type === "xtream") return loadXtreamLive(config);
    if (config.type === "m3u") return (await loadM3uCatalog(config)).live;
    throw new Error("Type de fournisseur non pris en charge.");
  }

  async function loadMovies(config) {
    if (!config || !config.type) throw new Error("Fournisseur non configuré.");
    if (config.type === "xtream") return loadXtreamMovies(config);
    if (config.type === "m3u") return (await loadM3uCatalog(config)).movies;
    return [];
  }

  async function loadSeries(config) {
    if (!config || !config.type) throw new Error("Fournisseur non configuré.");
    if (config.type === "xtream") return loadXtreamSeries(config);
    if (config.type === "m3u") {
      return (await loadM3uCatalog(config)).series.map(({ episodesBySeason, ...item }) => item);
    }
    return [];
  }

  async function loadSeriesInfo(config, seriesId) {
    if (!config || !config.type) throw new Error("Fournisseur non configuré.");
    if (config.type === "xtream") return loadXtreamSeriesInfo(config, seriesId);
    if (config.type === "m3u") return loadM3uSeriesInfo(config, seriesId);
    throw new Error("Type de fournisseur non pris en charge.");
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
    loadM3uCatalog,
    loadM3uSeriesInfo,
    loadSeriesInfo,
    classifyM3uEntry,
    parseEpisodeIdentity,
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
