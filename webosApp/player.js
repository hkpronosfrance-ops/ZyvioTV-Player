(() => {
  "use strict";

  class WebOsPlayer {
    constructor(videoElement) {
      this.video = videoElement;
      this.currentUrl = null;
      this.buffering = false;

      if (this.video) {
        ["waiting", "stalled"].forEach((eventName) => {
          this.video.addEventListener(eventName, () => { this.buffering = true; });
        });
        ["playing", "canplay", "canplaythrough"].forEach((eventName) => {
          this.video.addEventListener(eventName, () => { this.buffering = false; });
        });
        ["ended", "emptied", "error"].forEach((eventName) => {
          this.video.addEventListener(eventName, () => { this.buffering = false; });
        });
      }
    }

    async play(url) {
      this.stop();
      if (!this.video) throw new Error("Lecteur indisponible.");
      this.currentUrl = url;
      this.video.src = url;
      this.video.hidden = false;
      this.buffering = true;
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

    isActive() {
      return Boolean(this.currentUrl);
    }

    isPlaying() {
      if (!this.video || !this.currentUrl) return false;
      return !this.video.paused &&
        !this.video.ended &&
        !this.buffering &&
        this.video.readyState >= 3;
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
      this.buffering = false;
    }
  }

  window.ZyvioPlayer = { create: (video) => new WebOsPlayer(video) };
})();
