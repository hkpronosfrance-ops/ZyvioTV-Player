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
  const catalogPanel = document.getElementById("catalog-panel");
  const catalogTitle = document.getElementById("catalog-title");
  const catalogCount = document.getElementById("catalog-count");
  const catalogGrid = document.getElementById("catalog-grid");
  const video = document.getElementById("tv-player");
  const player = window.ZyvioPlayer?.create(video);

  let currentSession = null;
  let currentPlaylist = null;
  let providerConfig = null;
  let liveChannels = [];
  let movies = [];
  let series = [];
  let episodes = [];
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
    movies = [];
    series = [];
    episodes = [];
    if (liveGrid) liveGrid.replaceChildren();
    if (catalogGrid) catalogGrid.replaceChildren();
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
    if (catalogPanel) catalogPanel.hidden = true;
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

    if (section === "movies") {
      loadMovies();
      return;
    }

    if (section === "series") {
      loadSeries();
      return;
    }

    if (section === "more") {
      if (accountPanel) accountPanel.hidden = false;
      setStatus("Plus");
      setTimeout(() => accountPanel?.querySelector("[data-focusable]")?.focus(), 0);
      return;
    }

    if (section === "favorites") {
      setStatus("Favoris — disponible dans une prochaine phase webOS.");
      return;
    }

    setStatus(section);
  }

  async function restoreProvider(session) {
    setStatus("Synchronisation du compte…");
    const restored = await window.ZyvioCloud.restorePrimaryProvider(session);
    currentPlaylist = restored.playlist;
    providerConfig = restored.providerConfig;
    liveChannels = [];
    movies = [];
    series = [];
    episodes = [];

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

  function renderCatalog(title, entries, kind) {
    if (!catalogPanel || !catalogGrid) return;
    hidePanels();
    catalogPanel.hidden = false;
    if (catalogTitle) catalogTitle.textContent = title;
    if (catalogCount) {
      catalogCount.textContent = entries.length + " élément" + (entries.length > 1 ? "s" : "");
    }
    catalogGrid.replaceChildren();

    entries.slice(0, 80).forEach((entry) => {
      const button = document.createElement("button");
      button.className = "catalog-card";
      button.dataset.focusable = "";
      button.dataset.catalogKind = kind;
      button.dataset.catalogId = entry.id;

      const strong = document.createElement("strong");
      strong.textContent = entry.title || entry.name || "Contenu";

      const small = document.createElement("small");
      if (kind === "episode") {
        small.textContent = "S" + entry.season + "E" + entry.number;
      } else {
        small.textContent = entry.categoryName || "";
      }

      button.append(strong, small);
      catalogGrid.append(button);
    });

    setTimeout(() => catalogGrid.querySelector("[data-focusable]")?.focus(), 0);
  }

  async function loadMovies() {
    if (!providerConfig) {
      setStatus("Aucun fournisseur configuré.");
      return [];
    }
    if (providerConfig.type !== "xtream") {
      renderCatalog("Films", [], "movie");
      setStatus("Films indisponibles pour cette playlist M3U.");
      return [];
    }
    if (movies.length) {
      renderCatalog("Films", movies, "movie");
      setStatus(movies.length + " films chargés.");
      return movies;
    }

    setStatus("Chargement des films…");
    try {
      movies = await window.ZyvioProvider.loadMovies(providerConfig);
      renderCatalog("Films", movies, "movie");
      setStatus(movies.length + " films chargés.");
      return movies;
    } catch (error) {
      hidePanels();
      setStatus(error?.message || "Impossible de charger les films.");
      return [];
    }
  }

  async function loadSeries() {
    if (!providerConfig) {
      setStatus("Aucun fournisseur configuré.");
      return [];
    }
    if (providerConfig.type !== "xtream") {
      renderCatalog("Séries", [], "series");
      setStatus("Séries indisponibles pour cette playlist M3U.");
      return [];
    }
    if (series.length) {
      renderCatalog("Séries", series, "series");
      setStatus(series.length + " séries chargées.");
      return series;
    }

    setStatus("Chargement des séries…");
    try {
      series = await window.ZyvioProvider.loadSeries(providerConfig);
      renderCatalog("Séries", series, "series");
      setStatus(series.length + " séries chargées.");
      return series;
    } catch (error) {
      hidePanels();
      setStatus(error?.message || "Impossible de charger les séries.");
      return [];
    }
  }

  function renderEpisodes(seriesItem, info) {
    episodes = [];
    Object.entries(info?.episodesBySeason || {}).forEach(([seasonKey, values]) => {
      (Array.isArray(values) ? values : []).forEach((episode) => {
        episodes.push({
          ...episode,
          season: Number(episode.season || seasonKey || 0),
          seriesId: seriesItem.id,
          seriesTitle: seriesItem.title,
        });
      });
    });
    episodes.sort((a, b) => a.season - b.season || a.number - b.number);
    renderCatalog(seriesItem.title, episodes, "episode");
  }

  async function playCatalogStream(streamUrl, metadata) {
    try {
      await player.play(streamUrl);
      activePlayback = metadata;
      setStatus("Lecture : " + metadata.title);
    } catch (_) {
      activePlayback = null;
      setStatus("Contenu indisponible.");
    }
  }

  async function playChannel(channel) {
    if (!channel) return;
    try {
      await player.play(channel.streamUrl);
      activePlayback = { type: "live", id: channel.id, title: channel.name };
      let epgSuffix = "";
      if (providerConfig?.type === "xtream") {
        try {
          const epg = await window.ZyvioProvider.loadXtreamShortEpg(providerConfig, channel.id, 6);
          const now = Math.floor(Date.now() / 1000);
          const current = epg.find((item) => item.start <= now && item.end > now);
          if (current?.title) epgSuffix = " — " + current.title;
        } catch (_) {}
      }
      setStatus("Lecture : " + channel.name + epgSuffix);
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

      if (catalogPanel && !catalogPanel.hidden) {
        catalogPanel.hidden = true;
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
    const target = event.target.closest("[data-section], [data-action], [data-channel-id], [data-catalog-kind]");
    if (!target) return;

    if (target.dataset.channelId) {
      const channel = liveChannels.find(
        (item) => String(item.id) === String(target.dataset.channelId)
      );
      await playChannel(channel);
      return;
    }

    if (target.dataset.catalogKind === "movie") {
      const movie = movies.find((item) => item.id === target.dataset.catalogId);
      if (movie) {
        await playCatalogStream(movie.streamUrl, {
          type: "movie",
          id: movie.id,
          title: movie.title,
        });
      }
      return;
    }

    if (target.dataset.catalogKind === "series") {
      const seriesItem = series.find((item) => item.id === target.dataset.catalogId);
      if (!seriesItem) return;
      setStatus("Chargement : " + seriesItem.title + "…");
      try {
        const info = await window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, seriesItem.id);
        renderEpisodes(seriesItem, info);
        setStatus(seriesItem.title + " — " + episodes.length + " épisode(s).");
      } catch (_) {
        setStatus("Détails de série indisponibles.");
      }
      return;
    }

    if (target.dataset.catalogKind === "episode") {
      const episode = episodes.find((item) => item.id === target.dataset.catalogId);
      if (episode?.streamUrl) {
        await playCatalogStream(episode.streamUrl, {
          type: "episode",
          id: episode.id,
          title: episode.seriesTitle + " — S" + episode.season + "E" + episode.number,
        });
      }
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