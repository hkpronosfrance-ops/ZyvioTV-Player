const assert = require("node:assert/strict");
global.window = global;
require("./player.js");
const listeners = {};
const video = {
  paused: true, ended: false, readyState: 4, currentTime: 30, duration: 100,
  hidden: true, src: "",
  addEventListener(name, handler) { listeners[name] = handler; },
  async play() { this.paused = false; listeners.playing?.(); },
  pause() { this.paused = true; },
  removeAttribute() { this.src = ""; },
  load() {},
};
(async () => {
  const player = global.ZyvioPlayer.create(video);
  await player.play("https://example.test/movie.mp4");
  assert.equal(player.isPlaying(), true);
  assert.equal(player.getPositionMs(), 30000);
  player.seekToMs(12000);
  assert.equal(player.getPositionMs(), 12000);
  listeners.waiting();
  assert.equal(player.isPlaying(), false);
  listeners.playing();
  assert.equal(player.isPlaying(), true);
  listeners.error();
  assert.equal(player.isPlaying(), false);
  assert.equal(player.buffering, false);
  listeners.ended();
  assert.equal(player.isPlaying(), false);
  player.stop();
  assert.equal(player.isActive(), false);
  assert.equal(player.buffering, false);
  assert.equal(player.ended, false);
  video.play = async () => { throw Error("Playback rejected"); };
  await assert.rejects(player.play("https://example.test/broken.mp4"));
  assert.equal(player.isActive(), false);
  console.log("webOS playback state and failure recovery OK");
})().catch(error => { console.error(error); process.exitCode = 1; });
