(() => {
  "use strict";

  const selector = "[data-focusable]";
  const status = document.getElementById("status");
  const authStatus = document.getElementById("auth-status");
  const authScreen = document.getElementById("auth-screen");
  const appShell = document.getElementById("app-shell");
  const emailInput = document.getElementById("auth-email");
  const passwordInput = document.getElementById("auth-password");
  const livePanel = document.getElementById("live-panel");
  const liveGrid = document.getElementById("live-grid");
  const liveCount = document.getElementById("live-count");
  const accountPanel = document.getElementById("account-panel");
  const video = document.getElementById("tv-player");
  const player = window.ZyvioPlayer?.create(video);

  let currentSession = null;
  let currentPlaylist = null;
  let providerConfig = null;
  let liveChannels = [];
  let activeSection = "home";
  let activePlayback = null;

  function items() {
    return Array.from(document.querySelectorAll(selector))
      .filter((el) => !el.disabled && el.offsetParent !== null);
  }

  function setStatus(text) {
    if (status) status.textContent = text || "";
  }

  function setAuthStatus(text) {
    if (authStatus) authStatus.textContent = text || "";
  }

  function showAuth(message = "") {
    player?.stop();
    activePlayback = null;
    providerConfig = null;
    currentPlaylist = null;
    liveChannels = [];
    if (liveGrid) liveGrid.replaceChildren();
    if (livePanel) livePanel.hidden = true;
    if (accountPanel) accountPanel.hidden = true;
    if (appShell) appShell.hidden = true;
    if (authScreen) authScreen.hidden = false;
    setAuthStatus(message);
    setTimeout(() => emailInput?.focus(), 0);
  }

  function showApp() {
    if (authScreen) authScreen.hidden = true;
    if (appShell) appShell.hidden = false;
    setTimeout(() => document.querySelector('[data-section="home"]')?.focus(), 0);
  }

  function nextFocus(current, direction) {
    const cr = current.getBoundingClientRect();
    const cx = cr.left + cr.width / 2;
    const cy = cr.top + cr.height / 2;

    return items().filter((el) => el !== current).map((el) => {
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      const dx = x - cx;
      const dy = y - cy;

      const valid =
        (direction === "left" && dx < -8) ||
        (direction === "right" && dx > 8) ||
        (direction === "up" && dy < -8) ||
        (direction === "down" && dy > 8);

      if (!valid) return null;
      const primary = direction === "left" || direction === "right" ? Math.abs(dx) : Math.abs(dy);
      const secondary = direction === "left" || direction === "right" ? Math.abs(dy) : Math.abs(dx);
      return { el, score: primary + secondary * 2.25 };
    }).filter(Boolean).sort((a, b) => a.score - b.score)[0]?.el || null;
  }

  function move(direction) {
    const current = document.activeElement;
    const all = items();
    if (!all.length) return;

    if (!current || !current.matches(selector)) {
      all[0].focus();
      return;
    }

    const target = nextFocus(current, direction);
    if (target) {
      target.focus();
      target.scrollIntoView({ block: "nearest", inline: "nearest" });
    }
  }

  function hidePanels() {
    if (livePanel) livePanel.hidden = true;
    if (accountPanel) accountPanel.hidden = true;
  }

  function activate(section) {
    activeSection = section;
    document.querySelectorAll(".nav").forEach((item) => {
      item.classList.toggle("active", item.dataset.section === section);
    });

    hidePanels();

    if (section === "home") {
      setStatus(
        currentPlaylist?.name
          ? "Playlist restaurée : " + currentPlaylist.name
          : "Accueil"
      );
      return;
    }

    if (section === "live") {
      loadProviderLive();
      return;
    }

    if (section === "more") {
      if (accountPanel) accountPanel.hidden = false;
      setStatus("Plus");
      setTimeout(() => accountPanel?.querySelector("[data-focusable]")?.focus(), 0);
      return;
    }

    const label = {
      movies: "Films",
      series: "Séries",
      favorites: "Favoris",
    }[section] || section;

    setStatus(label + " — disponible dans une prochaine phase webOS.");
  }

  async function restoreProvider(session) {
    setStatus("Synchronisation du compte…");
    const restored = await window.ZyvioCloud.restorePrimaryProvider(session);
    currentPlaylist = restored.playlist;
    providerConfig = restored.providerConfig;
    liveChannels = [];

    if (!providerConfig) {
      setStatus("Aucune playlist active configurée sur ce compte.");
      return;
    }

    setStatus(
      currentPlaylist?.name
        ? "Playlist restaurée : " + currentPlaylist.name
        : "Playlist restaurée."
    );
  }

  async function restoreAccount() {
    setAuthStatus("Restauration de la session…");
    try {
      currentSession = await window.ZyvioAuth.restoreSession();
      if (!currentSession) {
        showAuth("");
        return;
      }
      showApp();
      await restoreProvider(currentSession);
    } catch (_) {
      currentSession = null;
      window.ZyvioAuth.clearSession();
      showAuth("Session expirée. Reconnectez-vous.");
    }
  }

  async function signIn() {
    const email = String(emailInput?.value || "").trim();
    const password = String(passwordInput?.value || "");

    if (!email || !password) {
      setAuthStatus("Renseignez votre adresse e-mail et votre mot de passe.");
      return;
    }

    setAuthStatus("Connexion…");
    try {
      currentSession = await window.ZyvioAuth.signIn(email, password);
      if (passwordInput) passwordInput.value = "";
      showApp();
      await restoreProvider(currentSession);
      setAuthStatus("");
    } catch (error) {
      currentSession = null;
      setAuthStatus(error?.message || "Connexion impossible.");
      passwordInput?.focus();
    }
  }

  async function signOut() {
    setStatus("Déconnexion…");
    try { await window.ZyvioAuth.signOut(); } catch (_) {}
    currentSession = null;
    showAuth("Vous êtes déconnecté.");
  }

  function renderLive() {
    if (!livePanel || !liveGrid) return;
    livePanel.hidden = false;
    liveGrid.replaceChildren();

    const visible = liveChannels.slice(0, 80);
    if (liveCount) {
      liveCount.textContent = liveChannels.length + " chaîne" + (liveChannels.length > 1 ? "s" : "");
    }

    visible.forEach((channel) => {
      const button = document.createElement("button");
      button.className = "catalog-card";
      button.dataset.focusable = "";
      button.dataset.channelId = channel.id;

      const strong = document.createElement("strong");
      strong.textContent = channel.name || "Chaîne";

      const small = document.createElement("small");
      small.textContent = channel.categoryName || "";

      button.append(strong, small);
      liveGrid.append(button);
    });

    setTimeout(() => liveGrid.querySelector("[data-focusable]")?.focus(), 0);
  }

  async function loadProviderLive() {
    if (!providerConfig) {
      setStatus("Aucun fournisseur configuré.");
      return [];
    }

    if (liveChannels.length) {
      renderLive();
      setStatus(liveChannels.length + " chaînes chargées.");
      return liveChannels;
    }

    setStatus("Chargement des chaînes…");
    try {
      liveChannels = await window.ZyvioProvider.loadLive(providerConfig);
      renderLive();
      setStatus(liveChannels.length + " chaînes chargées.");
      return liveChannels;
    } catch (error) {
      if (livePanel) livePanel.hidden = true;
      setStatus(error?.message || "Impossible de charger les chaînes.");
      return [];
    }
  }

  async function playChannel(channel) {
    if (!channel) return;
    try {
      await player.play(channel.streamUrl);
      activePlayback = { type: "live", id: channel.id, title: channel.name };
      setStatus("Lecture : " + channel.name);
    } catch (_) {
      activePlayback = null;
      setStatus("Flux indisponible.");
    }
  }

  function stopPlayback() {
    if (!activePlayback && !player?.currentUrl) return false;
    player?.stop();
    activePlayback = null;
    setStatus("Lecture arrêtée.");
    setTimeout(() => document.querySelector('[data-section="live"]')?.focus(), 0);
    return true;
  }

  function exitApp() {
    try {
      window.webOS?.platformBack?.();
      return;
    } catch (_) {}
    try {
      window.close();
    } catch (_) {}
  }

  document.addEventListener("keydown", (event) => {
    const directions = {
      ArrowLeft: "left",
      ArrowRight: "right",
      ArrowUp: "up",
      ArrowDown: "down",
    };

    if (directions[event.key]) {
      event.preventDefault();
      move(directions[event.key]);
      return;
    }

    if (event.key === "MediaPlayPause") {
      event.preventDefault();
      if (player?.video?.paused) player.resume(); else player?.pause();
      return;
    }
    if (event.key === "MediaPlay") {
      event.preventDefault();
      player?.resume();
      return;
    }
    if (event.key === "MediaPause") {
      event.preventDefault();
      player?.pause();
      return;
    }
    if (event.key === "MediaStop") {
      event.preventDefault();
      stopPlayback();
      return;
    }

    if (event.key === "Enter") {
      const current = document.activeElement;
      if (current?.click) {
        event.preventDefault();
        current.click();
      }
      return;
    }

    if (event.keyCode === 461 || event.key === "Escape" || event.key === "Backspace") {
      event.preventDefault();

      if (stopPlayback()) return;

      if (livePanel && !livePanel.hidden) {
        livePanel.hidden = true;
        activate("home");
        setTimeout(() => document.querySelector('[data-section="home"]')?.focus(), 0);
        return;
      }

      if (accountPanel && !accountPanel.hidden) {
        accountPanel.hidden = true;
        activate("home");
        setTimeout(() => document.querySelector('[data-section="home"]')?.focus(), 0);
        return;
      }

      if (authScreen && !authScreen.hidden) {
        exitApp();
        return;
      }

      if (activeSection !== "home") {
        activate("home");
        setTimeout(() => document.querySelector('[data-section="home"]')?.focus(), 0);
        return;
      }

      exitApp();
    }
  });

  document.addEventListener("click", async (event) => {
    const target = event.target.closest("[data-section], [data-action], [data-channel-id]");
    if (!target) return;

    if (target.dataset.channelId) {
      const channel = liveChannels.find(
        (item) => String(item.id) === String(target.dataset.channelId)
      );
      await playChannel(channel);
      return;
    }

    if (target.dataset.action === "sign-in") {
      await signIn();
      return;
    }

    if (target.dataset.action === "sign-out") {
      await signOut();
      return;
    }

    if (target.dataset.section) {
      activate(target.dataset.section);
    }
  });

  window.addEventListener("load", restoreAccount);
})();