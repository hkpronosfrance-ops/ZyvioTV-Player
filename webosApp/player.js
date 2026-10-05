(() => {
  "use strict";

  class WebOsPlayer {
    constructor(videoElement) {
      this.video = videoElement;
      this.currentUrl = null;
    }

    async play(url) {
      this.stop();
      if (!this.video) throw new Error("Lecteur indisponible.");
      this.currentUrl = url;
      this.video.src = url;
      this.video.hidden = false;
      await this.video.play();
    }

    pause() {
      this.video?.pause();
    }

    resume() {
      this.video?.play().catch(() => {});
    }

    stop() {
      if (!this.video) return;
      this.video.pause();
      this.video.removeAttribute("src");
      this.video.load();
      this.video.hidden = true;
      this.currentUrl = null;
    }
  }

  window.ZyvioPlayer = { create: (video) => new WebOsPlayer(video) };
})();
