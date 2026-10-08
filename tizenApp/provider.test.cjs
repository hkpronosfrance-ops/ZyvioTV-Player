global.window = global;
const assert = require("node:assert/strict");
const provider = require("./provider.js");

(async () => {
  const parsed = provider.parseM3u(
    [
      "#EXTM3U",
      '#EXTINF:-1 tvg-id="a1" tvg-logo="https://img.example/live.png" group-title="FR - LIVE",Actu 24',
      "https://stream.example/live/1.ts",
      '#EXTINF:-1 tvg-logo="https://img.example/movie.png" group-title="FR - FILMS",Film Exemple',
      "https://stream.example/movie/2.mkv",
      '#EXTINF:-1 tvg-logo="https://img.example/show.png" group-title="FR - SERIES",Ma Serie S01E02 Episode Deux',
      "https://stream.example/series/3.mkv",
      '#EXTINF:-1 group-title="TV SHOWS",Autre Serie 2x03 Episode Trois',
      "https://stream.example/series/4.mp4",
      "",
    ].join("\n")
  );

  assert.equal(parsed.length, 4);
  assert.equal(parsed[0].name, "Actu 24");
  assert.equal(parsed[0].categoryName, "FR - LIVE");
  assert.equal(parsed[0].epgChannelId, "a1");
  assert.ok(parsed.every((item) => item.id.startsWith("m3u-")));

  const episode1 = provider.parseEpisodeIdentity("Ma Serie S01E02 Episode Deux");
  assert.equal(episode1.seriesTitle, "Ma Serie");
  assert.equal(episode1.season, 1);
  assert.equal(episode1.episode, 2);

  const episode2 = provider.parseEpisodeIdentity("Autre Serie 2x03 Episode Trois");
  assert.equal(episode2.seriesTitle, "Autre Serie");
  assert.equal(episode2.season, 2);
  assert.equal(episode2.episode, 3);

  assert.equal(provider.classifyM3uEntry(parsed[0]).type, "live");
  assert.equal(provider.classifyM3uEntry(parsed[1]).type, "movie");
  assert.equal(provider.classifyM3uEntry(parsed[2]).type, "episode");
  assert.equal(provider.classifyM3uEntry(parsed[3]).type, "episode");

  // Category or URL alone cannot manufacture a season/episode.
  const unknownEpisode = {
    name: "Unnamed Show", categoryName: "FR - SERIES",
    streamUrl: "https://stream.example/series/99.mkv"
  };
  assert.notEqual(provider.classifyM3uEntry(unknownEpisode).type, "episode");
  const urlOnly = {
    name: "Unknown Show", categoryName: "Unclassified",
    streamUrl: "https://stream.example/series/100.mkv"
  };
  assert.notEqual(provider.classifyM3uEntry(urlOnly).type, "episode");
  assert.equal(provider.classifyM3uEntry({
    ...unknownEpisode, name: "Named Show S03E04"
  }).type, "episode");

  const m3uText = [
    "#EXTM3U",
    '#EXTINF:-1 tvg-id="a1" group-title="FR - LIVE",Actu 24',
    "https://stream.example/live/1.ts",
    '#EXTINF:-1 group-title="FR - FILMS",Film Exemple',
    "https://stream.example/movie/2.mkv",
    '#EXTINF:-1 group-title="FR - SERIES",Ma Serie S01E01 Pilote',
    "https://stream.example/series/3.mkv",
    '#EXTINF:-1 group-title="FR - SERIES",Ma Serie S01E02 Suite',
    "https://stream.example/series/4.mkv",
    "",
  ].join("\n");

  global.fetch = async () => ({
    ok: true,
    async text() { return m3uText; },
  });

  const config = { type: "m3u", url: "https://provider.example/list.m3u" };
  const catalog = await provider.loadM3uCatalog(config);
  assert.equal(catalog.live.length, 1, "M3U must expose Live");
  assert.equal(catalog.movies.length, 1, "M3U must expose Movies");
  assert.equal(catalog.series.length, 1, "M3U must expose Series");
  assert.equal(catalog.series[0].title, "Ma Serie");
  assert.equal(catalog.series[0].episodesBySeason["1"].length, 2, "M3U series must expose episodes");

  assert.equal((await provider.loadLive(config)).length, 1);
  assert.equal((await provider.loadMovies(config)).length, 1);
  assert.equal((await provider.loadSeries(config)).length, 1);

  const info = await provider.loadSeriesInfo(config, catalog.series[0].id);
  assert.equal(info.seasons.length, 1);
  assert.equal(info.episodesBySeason["1"][0].number, 1);
  assert.equal(info.episodesBySeason["1"][1].number, 2);

  const xtream = provider.xtreamLiveStreamUrl(
    { serverUrl: "https://provider.example", username: "alice", password: "secret" },
    42
  );
  assert.equal(xtream, "https://provider.example/live/alice/secret/42.ts");
  const redacted = provider.redactUrl(xtream);
  assert.ok(!redacted.includes("alice"));
  assert.ok(!redacted.includes("secret"));

  const movieUrl = provider.xtreamMovieStreamUrl(
    { serverUrl: "https://provider.example", username: "alice", password: "secret" },
    99,
    "mkv"
  );
  assert.equal(movieUrl, "https://provider.example/movie/alice/secret/99.mkv");

  const seriesInfoUrl = provider.xtreamSeriesInfoUrl(
    { serverUrl: "https://provider.example", username: "alice", password: "secret" },
    7
  );
  assert.ok(seriesInfoUrl.includes("action=get_series_info"));
  assert.ok(seriesInfoUrl.includes("series_id=7"));

  console.log("TV provider core OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
