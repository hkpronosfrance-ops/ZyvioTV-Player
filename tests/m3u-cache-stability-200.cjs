"use strict";
const assert = require("node:assert/strict");
const platform = process.argv[2];
if (!["tizenApp", "webosApp"].includes(platform)) throw Error("Specify TV platform");
global.window = global;
const realNow = Date.now;
let now = 1000000;
Date.now = () => now;
let calls = 0;
global.fetch = async () => {
  calls++;
  return {
    ok: true,
    async text() { return "#EXTM3U\n#EXTINF:-1 group-title=\"TV\",Channel\nhttps://example.invalid/live/1.ts"; },
  };
};
const provider = require("../" + platform + "/provider.js");
(async () => {
  const config = {type:"m3u",url:"https://example.invalid/playlist1.m3u"};
  const [one,two] = await Promise.all([provider.loadM3u(config),provider.loadM3u(config)]);
  assert.equal(calls,1,"deduplicate concurrent fetch");
  assert.equal(one.length,1);
  assert.deepEqual(one,two);
  now += 60*1000;
  await provider.loadM3u(config);
  assert.equal(calls,1,"reuse fresh cache");
  now += 5*60*1000;
  await provider.loadM3u(config);
  assert.equal(calls,2,"refresh expired cache");
  for (let i=2;i<=5;i++) await provider.loadM3u({type:"m3u",url:"https://example.invalid/playlist"+i+".m3u"});
  await provider.loadM3u(config);
  assert.equal(calls,7,"evict oldest cache keys after bound");
  console.log(platform+" M3U bounded TTL cache tests passed");
})().catch(error=>{console.error(error);process.exitCode=1}).finally(()=>{Date.now=realNow});
