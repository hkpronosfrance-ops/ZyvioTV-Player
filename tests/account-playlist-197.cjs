"use strict";
const assert = require("node:assert/strict");
const platform = process.argv[2];
if (!["tizenApp", "webosApp"].includes(platform)) throw Error("TV platform required");
global.window = { ZyvioAuth: { constants: { supabaseUrl: "https://example.invalid", publishableKey: "public-test-key" } } };
require("../" + platform + "/cloud.js");
const cloud = global.window.ZyvioCloud;
const goodM3u = cloud.providerConfigFromSecret({provider_type:"m3u",url:"https://example.invalid/list.m3u"});
assert.equal(goodM3u.type,"m3u");
const goodXtream = cloud.providerConfigFromSecret({provider_type:"xtream",server_url:"https://example.invalid",username:"test",password:"secret"});
assert.equal(goodXtream.type,"xtream");
for (const url of ["", "file:///storage/list.m3u", "javascript:alert(1)", "not-a-url"]) {
  assert.throws(()=>cloud.providerConfigFromSecret({provider_type:"m3u",url}),/Configuration M3U incomplète/);
  assert.throws(()=>cloud.providerConfigFromSecret({provider_type:"xtream",server_url:url,username:"test",password:"secret"}),/Configuration Xtream incomplète/);
}
console.log(platform+" restored playlist validation OK");
