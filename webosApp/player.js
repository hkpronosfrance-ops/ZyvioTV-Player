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

    getPositionMs() {
      return this.video
        ? Math.max(0, Math.floor((this.video.currentTime || 0) * 1000))
        : 0;
    }

    getDurationMs() {
      return this.video && Number.isFinite(this.video.duration)
        ? Math.max(0, Math.floor(this.video.duration * 1000))
        : 0;
    }

    seekToMs(positionMs) {
      if (!this.video) return;
      const safe = Math.max(0, Number(positionMs || 0));
      try { this.video.currentTime = safe / 1000; } catch (_) {}
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
