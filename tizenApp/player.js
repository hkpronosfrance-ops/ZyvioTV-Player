(() => {
  "use strict";

  class TizenPlayer {
    constructor(videoElement) {
      this.video = videoElement;
      this.usingAvPlay = Boolean(window.webapis?.avplay);
      this.currentUrl = null;
      this.paused = false;
      this.buffering = false;
      this.ended = false;

      if (this.usingAvPlay) {
        try {
          window.webapis.avplay.setListener({
            onbufferingstart: () => { this.buffering = true; },
            onbufferingcomplete: () => { this.buffering = false; },
            onstreamcompleted: () => {
              this.ended = true;
              this.buffering = false;
              this.paused = false;
            },
            onerror: () => {
              this.buffering = false;
              this.ended = true;
            },
          });
        } catch (_) {}
      } else if (this.video) {
        ["waiting", "stalled"].forEach((eventName) => {
          this.video.addEventListener(eventName, () => { this.buffering = true; });
        });
        ["playing", "canplay", "canplaythrough"].forEach((eventName) => {
          this.video.addEventListener(eventName, () => {
            this.buffering = false;
            this.ended = false;
          });
        });
        this.video.addEventListener("ended", () => {
          this.ended = true;
          this.buffering = false;
        });
        this.video.addEventListener("error", () => {
          this.buffering = false;
          this.ended = true;
        });
      }
    }

    async play(url) {
      this.stop();
      if (typeof url !== "string" || !/^https?:\/\//i.test(url.trim())) {
        throw new Error("Adresse du flux vidéo invalide.");
      }
      this.currentUrl = url;
      this.buffering = true;
      this.ended = false;

      if (this.usingAvPlay) {
        const avplay = window.webapis.avplay;
        try {
          avplay.open(url);
          avplay.setDisplayRect(0, 0, 1920, 1080);
          avplay.setDisplayMethod("PLAYER_DISPLAY_MODE_FULL_SCREEN");
          await new Promise((resolve, reject) => {
            avplay.prepareAsync(resolve, () => reject(new Error("Lecture impossible.")));
          });
          avplay.play();
        } catch (error) {
          this.stop();
          throw error;
        }
        this.paused = false;
        this.buffering = false;
        this.ended = false;
        return;
      }

      if (!this.video) throw new Error("Lecteur indisponible.");
      this.video.src = url;
      this.video.hidden = false;
      try {
        await this.video.play();
      } catch (error) {
        this.stop();
        throw error;
      }
      this.paused = false;
      this.ended = false;
    }

    pause() {
      if (!this.currentUrl) return;
      if (this.usingAvPlay) {
        try { window.webapis.avplay.pause(); } catch (_) {}
      } else if (this.video) {
        this.video.pause();
      }
      this.paused = true;
    }

    resume() {
      if (!this.currentUrl) return;
      if (this.usingAvPlay) {
        try { window.webapis.avplay.play(); } catch (_) {}
      } else if (this.video) {
        this.video.play().catch(() => {
          this.paused = true;
          this.buffering = false;
        });
      }
      this.paused = false;
      this.ended = false;
    }

    isPlaying() {
      if (!this.currentUrl || this.paused || this.buffering || this.ended) return false;

      if (this.usingAvPlay) {
        try {
          return window.webapis.avplay.getState() === "PLAYING";
        } catch (_) {
          return false;
        }
      }

      if (!this.video) return false;
      return !this.video.paused &&
        !this.video.ended &&
        this.video.readyState >= 3;
    }

    togglePause() {
      if (!this.currentUrl) return false;
      if (this.paused) this.resume(); else this.pause();
      return true;
    }

    seekByMs(deltaMs) {
      if (!this.currentUrl) return false;
      const duration = this.getDurationMs();
      const current = this.getPositionMs();
      const upper = duration > 0 ? duration : Number.MAX_SAFE_INTEGER;
      this.seekToMs(Math.max(0, Math.min(upper, current + Number(deltaMs || 0))));
      return true;
    }

    isActive() {
      return Boolean(this.currentUrl);
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
      this.paused = false;
      this.buffering = false;
      this.ended = false;
    }
  }

  window.ZyvioPlayer = { create: (video) => new TizenPlayer(video) };
})();
