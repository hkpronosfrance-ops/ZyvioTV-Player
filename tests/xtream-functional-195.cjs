"use strict";
const assert = require("node:assert/strict");
const platform = process.argv[2];
if (!["webosApp", "tizenApp"].includes(platform)) throw Error("Specify TV platform");
global.window = global;
const provider = require("../" + platform + "/provider.js");
const responses = {
  get_live_categories: [{ category_id: "2", category_name: "TV" }],
  get_live_streams: [{ stream_id: 101, name: "Journal", category_id: "2" }, { name: "Missing ID" }],
  get_vod_categories: [{ category_id: "3", category_name: "Cinéma" }],
  get_vod_streams: [{ stream_id: 102, name: "Film", category_id: "3", container_extension: "mkv" }, { stream_id: null, name: "Invalid" }],
  get_series_categories: [{ category_id: "4", category_name: "Séries" }],
  get_series: [{ series_id: 103, name: "Série", category_id: "4" }, { name: "Missing ID" }],
  get_series_info: {
    seasons: [{ season_number: 2, name: "Saison deux" }],
    episodes: { "2": [{ id: 104, episode_num: 5, title: "Épisode 5", container_extension: "mp4" }, { title: "Missing Stream" }] },
  },
};
global.fetch = async url => {
  const parsed = new URL(url);
  return { ok: true, async json(){return responses[parsed.searchParams.get("action")] ?? [];} };
};
(async () => {
  const config = { type: "xtream", serverUrl: "https://example.invalid", username: "user", password: "password" };
  const live = await provider.loadLive(config);
  const movies = await provider.loadMovies(config);
  const series = await provider.loadSeries(config);
  assert.equal(live.length, 1);
  assert.equal(movies.length, 1);
  assert.equal(series.length, 1);
  assert.equal(live[0].id, "101");
  assert.equal(movies[0].id, "102");
  assert.equal(series[0].id, "103");
  assert.match(live[0].streamUrl, /\/live\/user\/password\/101\.ts$/);
  assert.match(movies[0].streamUrl, /\/movie\/user\/password\/102\.mkv$/);
  const info = await provider.loadSeriesInfo(config, "103");
  assert.equal(info.episodesBySeason["2"].length, 1);
  assert.equal(info.episodesBySeason["2"][0].number, 5);
  assert.match(info.episodesBySeason["2"][0].streamUrl, /\/series\/user\/password\/104\.mp4$/);
  assert.equal(info.seasons.length, 1);
  console.log(platform + " Xtream Live/VOD/Series validation OK");
})().catch(e => { console.error(e); process.exitCode = 1; });
