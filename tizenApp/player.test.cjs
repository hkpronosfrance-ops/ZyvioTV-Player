const assert = require("assert");

function loadPlayer(windowMock) {
  delete require.cache[require.resolve("./player.js")];
  global.window = windowMock;
  require("./player.js");
  return windowMock.ZyvioPlayer;
}

function createVideoMock() {
  const listeners = {};
  return {
    paused: true,
    ended: false,
    readyState: 4,
    currentTime: 0,
    duration: 100,
    hidden: true,
    src: "",
    addEventListener(name, fn) { listeners[name] = fn; },
    async play() { this.paused = false; listeners.playing?.(); },
    pause() { this.paused = true; },
    removeAttribute() { this.src = ""; },
    load() {},
    fire(name) { listeners[name]?.(); },
  };
}

(async () => {
  {
    const video = createVideoMock();
    const api = loadPlayer({});
    const player = api.create(video);

    await player.play("https://example.test/movie.mp4");
    assert.equal(player.isPlaying(), true, "HTML5 playing should count");

    player.pause();
    assert.equal(player.isPlaying(), false, "HTML5 paused must not count");

    player.resume();
    await Promise.resolve();
    assert.equal(player.isPlaying(), true, "HTML5 resumed should count");

    video.fire("waiting");
    assert.equal(player.isPlaying(), false, "HTML5 buffering must not count");

    video.fire("playing");
    assert.equal(player.isPlaying(), true, "HTML5 playing after buffer should count");

    video.ended = true;
    video.fire("ended");
    assert.equal(player.isPlaying(), false, "HTML5 ended must not count");
    player.stop();
    assert.equal(player.buffering, false);
    assert.equal(player.ended, false);
    assert.equal(player.isActive(), false);
  }

  {
    let listener = null;
    let state = "NONE";
    const avplay = {
      setListener(value) { listener = value; },
      open() { state = "IDLE"; },
      setDisplayRect() {},
      setDisplayMethod() {},
      prepareAsync(ok) { state = "READY"; ok(); },
      play() { state = "PLAYING"; },
      pause() { state = "PAUSED"; },
      stop() { state = "IDLE"; },
      close() { state = "NONE"; },
      getState() { return state; },
      getCurrentTime() { return 1000; },
      getDuration() { return 10000; },
      seekTo() {},
    };

    const api = loadPlayer({ webapis: { avplay } });
    const player = api.create(null);

    await player.play("https://example.test/live.ts");
    assert.equal(player.isPlaying(), true, "AVPlay PLAYING should count");

    player.pause();
    assert.equal(player.isPlaying(), false, "AVPlay paused must not count");

    player.resume();
    assert.equal(player.isPlaying(), true, "AVPlay resumed should count");

    listener.onbufferingstart();
    assert.equal(player.isPlaying(), false, "AVPlay buffering must not count");

    listener.onbufferingcomplete();
    assert.equal(player.isPlaying(), true, "AVPlay after buffering should count");

    listener.onstreamcompleted();
    assert.equal(player.isPlaying(), false, "AVPlay completed stream must not count");
    player.stop();
    assert.equal(player.buffering, false);
    assert.equal(player.ended, false);
  }

  delete global.window;
  console.log("Tizen player state tests passed");
})().catch((error) => {
  delete global.window;
  console.error(error);
  process.exit(1);
});
