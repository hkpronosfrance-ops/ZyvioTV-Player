"use strict";
const assert = require("node:assert/strict");
global.window = global;
const platform = process.argv[2];
if (!["tizenApp","webosApp"].includes(platform)) throw Error("Specify tizenApp or webosApp");
const provider = require("../" + platform + "/provider.js");
const playlist = [
  "#EXTM3U",
  '#EXTINF:-1 group-title="FR - TV",Chaîne exemple',
  "https://example.invalid/live/101.ts",
  '#EXTINF:-1 group-title="FR - FILMS",Film exemple',
  "https://example.invalid/movie/101.mp4",
  '#EXTINF:-1 group-title="FR - Séries",Une série S01E02',
  "https://example.invalid/series/1.mp4",
  '#EXTINF:-1 group-title="EN - Series",Une série S02E03',
  "https://example.invalid/series/2.mp4",
  '#EXTINF:-1 group-title="FR - Séries",Une série sans numéro',
  "https://example.invalid/series/3.mp4",
].join("\n");
global.fetch = async () => ({ok:true,text:async()=>playlist});
(async () => {
  const config={type:"m3u",url:"https://example.invalid/playlist.m3u"};
  const live=await provider.loadLive(config);
  const movies=await provider.loadMovies(config);
  const series=await provider.loadSeries(config);
  assert.equal(live.length,1);
  assert.equal(movies.length,2,"an episode without explicit number may be VOD, but not a fabricated series episode");
  assert.equal(series.length,2,"same-titled series in separate category must not collide");
  assert.notEqual(series[0].id,series[1].id);
  const details=await Promise.all(series.map(s=>provider.loadSeriesInfo(config,s.id)));
  assert.deepEqual(details.map(x=>Object.keys(x.episodesBySeason)).sort(),[["1"],["2"]]);
  assert.deepEqual(details.map(x=>Object.values(x.episodesBySeason).flat().map(e=>e.number)).sort(),[[2],[3]]);
  console.log(platform+" M3U live/movie/series category isolation OK");
})().catch(err=>{console.error(err);process.exitCode=1;});
