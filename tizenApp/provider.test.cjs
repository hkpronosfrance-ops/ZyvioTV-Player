global.window = global;
const assert = require("node:assert/strict");
const provider = require("./provider.js");

const parsed = provider.parseM3u(
  '#EXTM3U\n#EXTINF:-1 tvg-id="a1" tvg-logo="https://img.example/logo.png" group-title="News",Actu 24\nhttps://stream.example/live.m3u8\n'
);
assert.equal(parsed.length, 1);
assert.equal(parsed[0].name, "Actu 24");
assert.equal(parsed[0].categoryName, "News");
assert.equal(parsed[0].epgChannelId, "a1");

const xtream = provider.xtreamLiveStreamUrl(
  { serverUrl: "https://provider.example", username: "alice", password: "secret" },
  42
);
assert.equal(xtream, "https://provider.example/live/alice/secret/42.ts");
const redacted = provider.redactUrl(xtream);
assert.ok(!redacted.includes("alice"));
assert.ok(!redacted.includes("secret"));
assert.ok(redacted.includes("[REDACTED]"));

console.log("TV provider core OK");
