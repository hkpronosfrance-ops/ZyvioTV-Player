(() => {
  "use strict";

  class TizenPlayer {
    constructor(videoElement) {
      this.video = videoElement;
      this.usingAvPlay = Boolean(window.webapis?.avplay);
      this.currentUrl = null;
    }

    async play(url) {
      this.stop();
      this.currentUrl = url;

      if (this.usingAvPlay) {
        const avplay = window.webapis.avplay;
        avplay.open(url);
        avplay.setDisplayRect(0, 0, 1920, 1080);
        avplay.setDisplayMethod("PLAYER_DISPLAY_MODE_FULL_SCREEN");
        await new Promise((resolve, reject) => {
          avplay.prepareAsync(resolve, () => reject(new Error("Lecture impossible.")));
        });
        avplay.play();
        return;
      }

      if (!this.video) throw new Error("Lecteur indisponible.");
      this.video.src = url;
      this.video.hidden = false;
      await this.video.play();
    }

    pause() {
      if (this.usingAvPlay) {
        try { window.webapis.avplay.pause(); } catch (_) {}
      } else if (this.video) {
        this.video.pause();
      }
    }

    resume() {
      if (this.usingAvPlay) {
        try { window.webapis.avplay.play(); } catch (_) {}
      } else if (this.video) {
        this.video.play().catch(() => {});
      }
    }

    getPositionMs() {
      if (this.usingAvPlay) {
        try { return Number(window.webapis.avplay.getCurrentTime() || 0); } catch (_) { return 0; }
      }
      return this.video ? Math.max(0, Math.floor((this.video.currentTime || 0) * 1000)) : 0;
    }

    getDurationMs() {
      if (this.usingAvPlay) {
        try { return Number(window.webapis.avplay.getDuration() || 0); } catch (_) { return 0; }
      }
      return this.video && Number.isFinite(this.video.duration)
        ? Math.max(0, Math.floor(this.video.duration * 1000))
        : 0;
    }

    seekToMs(positionMs) {
      const safe = Math.max(0, Number(positionMs || 0));
      if (this.usingAvPlay) {
        try { window.webapis.avplay.seekTo(safe); } catch (_) {}
      } else if (this.video) {
        try { this.video.currentTime = safe / 1000; } catch (_) {}
      }
    }

    stop() {
      if (this.usingAvPlay) {
        try { window.webapis.avplay.stop(); } catch (_) {}
        try { window.webapis.avplay.close(); } catch (_) {}
      } else if (this.video) {
        this.video.pause();
        this.video.removeAttribute("src");
        this.video.load();
        this.video.hidden = true;
      }
      this.currentUrl = null;
    }
  }

  window.ZyvioPlayer = { create: (video) => new TizenPlayer(video) };
})();
