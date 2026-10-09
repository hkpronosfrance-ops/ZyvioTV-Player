"use strict";
const assert = require("node:assert/strict");
const platform = process.argv[2];
if (!["tizenApp", "webosApp"].includes(platform)) throw Error("Specify TV platform");
global.window = global;
delete require.cache[require.resolve("../" + platform + "/player.js")];
require("../" + platform + "/player.js");
const video = {
  paused: true, hidden: true, src: "", currentTime: 0, duration: 0, readyState: 0,
  addEventListener() {}, pause() { this.paused = true; },
  removeAttribute() { this.src = ""; }, load() {},
  async play() { this.paused = false; }
};
(async()=>{
  const player=global.ZyvioPlayer.create(video);
  for (const bad of [null,undefined,"", "file:///tmp/video", "javascript:alert(1)"]) {
    await assert.rejects(player.play(bad), /Adresse du flux vidéo invalide/);
    assert.equal(player.isActive(), false);
    assert.equal(player.buffering, false);
  }
  await player.play("https://example.invalid/movie.mp4");
  assert.equal(player.isActive(), true);
  player.stop();
  assert.equal(player.isActive(), false);
  if (platform === "tizenApp") {
    const mock={open(){throw Error("AVPlay cannot open");},stop(){},close(){}};
    global.window={webapis:{avplay:mock}};
    delete require.cache[require.resolve("../tizenApp/player.js")];
    require("../tizenApp/player.js");
    const native=global.window.ZyvioPlayer.create(null);
    await assert.rejects(native.play("https://example.invalid/live.ts"),/AVPlay cannot open/);
    assert.equal(native.isActive(),false);
    assert.equal(native.buffering,false);
  }
  console.log(platform+" invalid stream/AVPlay recovery checks passed");
})().catch(e=>{console.error(e);process.exitCode=1});
