(() => {
  "use strict";

  const selector = "[data-focusable]";
  const status = document.getElementById("status");
  const authStatus = document.getElementById("auth-status");
  const authScreen = document.getElementById("auth-screen");
  const appShell = document.getElementById("app-shell");
  const profileScreen = document.getElementById("profile-screen");
  const profileGrid = document.getElementById("profile-grid");
  const profileStatus = document.getElementById("profile-status");
  const pinScreen = document.getElementById("pin-screen");
  const pinTitle = document.getElementById("pin-title");
  const pinCopy = document.getElementById("pin-copy");
  const pinInput = document.getElementById("pin-input");
  const pinStatus = document.getElementById("pin-status");
  const continueShelf = document.getElementById("continue-shelf");
  const continueCards = document.getElementById("continue-cards");
  const recentChannelsShelf = document.getElementById("recent-channels-shelf");
  const recentChannelCards = document.getElementById("recent-channel-cards");
  const favoritesShelf = document.getElementById("favorites-shelf");
  const favoriteCards = document.getElementById("favorite-cards");
  const historyShelf = document.getElementById("history-shelf");
  const historyCards = document.getElementById("history-cards");
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
  let profiles = [];
  let currentProfile = null;
  let favorites = [];
  let watchProgress = [];
  let liveHistory = [];
  let parentalSettings = null;
  let contentLocks = null;
  let pendingPinAction = null;
  let pendingPinVerifier = null;
  let parentalRuntime = null;
  let runtimeBlocked = false;
  let runtimeExceptionUntilMs = 0;

  const PROFILE_STORAGE_KEY = "zyviotv.webos.profile.v1";
  const DEVICE_UID_STORAGE_KEY = "zyviotv.webos.device_uid.v1";
  const RUNTIME_CACHE_PREFIX = "zyviotv.webos.parental_runtime.v1.";

  function stableDeviceUid() {
    try {
      const existing = localStorage.getItem(DEVICE_UID_STORAGE_KEY);
      if (existing) return existing;
      const random = window.crypto?.getRandomValues
        ? Array.from(window.crypto.getRandomValues(new Uint8Array(16)))
          .map((value) => value.toString(16).padStart(2, "0"))
          .join("")
        : Math.random().toString(36).slice(2) + Date.now().toString(36);
      const uid = "webos-" + random.slice(0, 48);
      localStorage.setItem(DEVICE_UID_STORAGE_KEY, uid);
      return uid;
    } catch (_) {
      return "webos-session";
    }
  }

  const deviceUid = stableDeviceUid();

  function runtimeCacheKey(profileId) {
    return RUNTIME_CACHE_PREFIX + String(profileId || "");
  }

  function utcDayKey(epochMs) {
    const date = new Date(Number(epochMs || 0));
    return [
      date.getUTCFullYear(),
      String(date.getUTCMonth() + 1).padStart(2, "0"),
      String(date.getUTCDate()).padStart(2, "0"),
    ].join("-");
  }

  function loadRuntimeCache(profileId) {
    if (!profileId) return null;
    try {
      const raw = localStorage.getItem(runtimeCacheKey(profileId));
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : null;
    } catch (_) {
      return null;
    }
  }

  function saveRuntimeCache(profileId, state) {
    if (!profileId || !state?.server_now_epoch_ms) return;
    const previous = loadRuntimeCache(profileId);
    const serverNow = Number(state.server_now_epoch_ms);
    const day = utcDayKey(serverNow);
    const consumed = previous?.usageDayUtc === day
      ? Math.max(Number(previous.consumedSeconds || 0), Number(state.consumed_seconds || 0))
      : Math.max(0, Number(state.consumed_seconds || 0));

    const payload = {
      parentalEnabled: Boolean(state.parental_enabled),
      isChild: Boolean(state.is_child),
      scheduleEnabled: Boolean(state.schedule_enabled),
      scheduleWindows: Array.isArray(state.schedule_windows) ? state.schedule_windows : [],
      trustedEpochMs: serverNow,
      trustedPerformanceMs: Number(performance.now() || 0),
      trustedWallClockMs: Date.now(),
      consumedSeconds: consumed,
      usageDayUtc: day,
      dailyLimitMinutes: state.daily_limit_minutes == null ? null : Number(state.daily_limit_minutes),
      weekendLimitMinutes: state.weekend_limit_minutes == null ? null : Number(state.weekend_limit_minutes),
      warningMinutes: Number(state.warning_minutes || 10),
    };
    try { localStorage.setItem(runtimeCacheKey(profileId), JSON.stringify(payload)); } catch (_) {}
    parentalRuntime = payload;
  }

  function trustedRuntimeNowMs(cache = parentalRuntime) {
    if (!cache) return 0;

    const currentPerf = Number(performance.now() || 0);
    const anchorPerf = Number(cache.trustedPerformanceMs || 0);
    if (currentPerf >= anchorPerf && anchorPerf > 0) {
      return Number(cache.trustedEpochMs || 0) + (currentPerf - anchorPerf);
    }

    const wallAnchor = Number(cache.trustedWallClockMs || 0);
    const wallNow = Date.now();
    const wallDelta = wallAnchor > 0 && wallNow >= wallAnchor
      ? Math.min(wallNow - wallAnchor, 24 * 60 * 60 * 1000)
      : 0;
    return Number(cache.trustedEpochMs || 0) + wallDelta;
  }

  function effectiveRuntimeLimitMinutes(cache = parentalRuntime) {
    if (!cache) return null;
    const now = new Date(trustedRuntimeNowMs(cache));
    const day = now.getUTCDay();
    const weekend = day === 0 || day === 6;
    return weekend
      ? (cache.weekendLimitMinutes ?? cache.dailyLimitMinutes)
      : cache.dailyLimitMinutes;
  }

  function scheduleAllowsNow(cache = parentalRuntime) {
    if (!cache?.parentalEnabled || !cache?.isChild || !cache?.scheduleEnabled) return true;
    const windows = Array.isArray(cache.scheduleWindows) ? cache.scheduleWindows : [];
    if (!windows.length) return false;

    const now = new Date(trustedRuntimeNowMs(cache));
    const jsDay = now.getDay();
    const isoDay = jsDay === 0 ? 7 : jsDay;
    const previousIsoDay = isoDay === 1 ? 7 : isoDay - 1;
    const minuteOfDay = now.getHours() * 60 + now.getMinutes();

    const minute = (value) => {
      const parts = String(value || "").split(":");
      if (parts.length !== 2) return null;
      const hour = Number(parts[0]);
      const min = Number(parts[1]);
      if (!Number.isInteger(hour) || !Number.isInteger(min) ||
          hour < 0 || hour > 23 || min < 0 || min > 59) return null;
      return hour * 60 + min;
    };

    for (const window of windows) {
      const days = Array.isArray(window?.days) ? window.days.map(Number) : [];
      const start = minute(window?.start);
      const end = minute(window?.end);
      if (start == null || end == null) continue;
      if (start === end && days.includes(isoDay)) return true;
      if (start < end && days.includes(isoDay) && minuteOfDay >= start && minuteOfDay < end) {
        return true;
      }
      if (start > end) {
        if (days.includes(isoDay) && minuteOfDay >= start) return true;
        if (days.includes(previousIsoDay) && minuteOfDay < end) return true;
      }
    }
    return false;
  }

  function updateLocalRuntimeConsumed(seconds) {
    if (!currentProfile || !parentalRuntime) return;
    const now = trustedRuntimeNowMs(parentalRuntime);
    const day = utcDayKey(now);
    if (parentalRuntime.usageDayUtc !== day) {
      parentalRuntime.consumedSeconds = 0;
      parentalRuntime.usageDayUtc = day;
    }
    parentalRuntime.consumedSeconds = Math.max(
      0,
      Number(parentalRuntime.consumedSeconds || 0) + Math.max(0, Number(seconds || 0))
    );
    try {
      localStorage.setItem(runtimeCacheKey(currentProfile.id), JSON.stringify(parentalRuntime));
    } catch (_) {}
  }

  function storedProfileId() {
    try { return localStorage.getItem(PROFILE_STORAGE_KEY); } catch (_) { return null; }
  }

  function persistProfileId(profileId) {
    try { localStorage.setItem(PROFILE_STORAGE_KEY, profileId); } catch (_) {}
  }

  function setProfileStatus(text) {
    if (profileStatus) profileStatus.textContent = text || "";
  }

  function libraryKey(playlistId, type, id) {
    return String(playlistId || "") + ":" + String(type || "") + ":" + String(id || "");
  }

  function favoriteKey(itemOrType, id = null, playlistId = null) {
    if (typeof itemOrType === "object" && itemOrType) {
      return libraryKey(itemOrType.playlist_id, itemOrType.content_type, itemOrType.content_id);
    }
    return libraryKey(playlistId || currentPlaylist?.id, itemOrType, id);
  }

  function isFavorite(type, id) {
    const key = favoriteKey(type, id);
    return favorites.some((item) => favoriteKey(item) === key);
  }

  function progressFor(type, id) {
    const key = libraryKey(currentPlaylist?.id, type, id);
    return watchProgress.find(
      (item) => libraryKey(item.playlist_id, item.content_type, item.content_id) === key
    ) || null;
  }

  function isChildProfile() {
    return currentProfile?.profile_type === "child";
  }

  function contentKey(type, id) {
    return String(type || "") + ":" + String(id || "");
  }

  function normalizedText(value) {
    return String(value || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
  }

  function isAdultLabel(value) {
    const text = normalizedText(value);
    return [
      "adult", "adulte", "xxx", "porn", "porno", "18+", "+18", "erotic", "erotique"
    ].some((token) => text.includes(token));
  }

  function isLockedForChild(type, item) {
    if (!isChildProfile()) return false;

    const category = item?.categoryName || item?.category_name || "";
    const title = item?.title || item?.name || "";
    if (isAdultLabel(category) || isAdultLabel(title)) return true;
    if (!contentLocks?.parental_enabled) return false;

    const lockedContent = new Set(contentLocks.locked_content_keys || []);
    const lockedCategories = new Set(contentLocks.locked_category_keys || []);
    const itemContentKey = contentKey(type, item?.id);
    const itemCategoryKey = contentKey(type, item?.categoryId || item?.category_id || "");

    if (lockedContent.has(itemContentKey)) return true;
    if ((item?.categoryId || item?.category_id) && lockedCategories.has(itemCategoryKey)) return true;
    return false;
  }

  function isLibraryItemLocked(item) {
    if (!isChildProfile()) return false;
    if (isAdultLabel(item?.title || "")) return true;
    if (!contentLocks?.parental_enabled) return false;

    const lockedContent = new Set(contentLocks.locked_content_keys || []);
    if (lockedContent.has(contentKey(item?.content_type, item?.content_id))) return true;
    if (item?.content_type === "episode" && item?.series_id) {
      if (lockedContent.has(contentKey("series", item.series_id))) return true;
    }
    return false;
  }

  function filterForProfile(type, entries) {
    return (Array.isArray(entries) ? entries : []).filter((item) => !isLockedForChild(type, item));
  }

  function showPinPrompt(title, copy, action, verifier = null) {
    pendingPinAction = typeof action === "function" ? action : null;
    pendingPinVerifier = typeof verifier === "function" ? verifier : null;
    if (pinTitle) pinTitle.textContent = title || "Code PIN requis";
    if (pinCopy) pinCopy.textContent = copy || "Saisissez le code PIN parental à 4 chiffres.";
    if (pinStatus) pinStatus.textContent = "";
    if (pinInput) pinInput.value = "";
    if (pinScreen) pinScreen.hidden = false;
    setTimeout(() => pinInput?.focus(), 0);
  }

  function closePinPrompt() {
    pendingPinAction = null;
    pendingPinVerifier = null;
    if (pinInput) pinInput.value = "";
    if (pinStatus) pinStatus.textContent = "";
    if (pinScreen) pinScreen.hidden = true;
  }

  async function verifyPendingPin() {
    const pin = String(pinInput?.value || "").replace(/\D/g, "").slice(0, 4);
    if (pin.length !== 4) {
      if (pinStatus) pinStatus.textContent = "Saisissez les 4 chiffres du code PIN.";
      return;
    }

    if (pinStatus) pinStatus.textContent = "Vérification…";
    try {
      if (typeof pendingPinVerifier === "function") {
        const verification = await pendingPinVerifier(pin);
        if (verification?.ok) {
          const action = pendingPinAction;
          closePinPrompt();
          if (action) await action();
          return;
        }
        if (pinStatus) pinStatus.textContent = verification?.message || "Code PIN non vérifié.";
        return;
      }

      const result = await window.ZyvioCloud.verifyParentalPin(currentSession, pin);
      if (!result?.verified) {
        const reason = result?.reason;
        if (pinStatus) {
          pinStatus.textContent = reason === "blocked"
            ? "Trop de tentatives. Réessayez dans quelques minutes."
            : reason === "pin_not_configured"
              ? "Aucun code PIN parental n’est configuré."
              : "Code PIN incorrect.";
        }
        if (pinInput) {
          pinInput.value = "";
          pinInput.focus();
        }
        return;
      }

      const action = pendingPinAction;
      closePinPrompt();
      if (action) await action();
    } catch (_) {
      if (pinStatus) pinStatus.textContent = "Impossible de vérifier le code PIN.";
    }
  }

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
    profiles = [];
    currentProfile = null;
    favorites = [];
    watchProgress = [];
    liveHistory = [];
    parentalSettings = null;
    contentLocks = null;
    pendingPinAction = null;
    pendingPinVerifier = null;
    parentalRuntime = null;
    runtimeBlocked = false;
    runtimeExceptionUntilMs = 0;
    liveChannels = [];
    movies = [];
    series = [];
    episodes = [];
    if (liveGrid) liveGrid.replaceChildren();
    if (catalogGrid) catalogGrid.replaceChildren();
    if (livePanel) livePanel.hidden = true;
    if (accountPanel) accountPanel.hidden = true;
    if (profileScreen) profileScreen.hidden = true;
    if (pinScreen) pinScreen.hidden = true;
    if (appShell) appShell.hidden = true;
    if (authScreen) authScreen.hidden = false;
    setAuthStatus(message);
    setTimeout(() => emailInput?.focus(), 0);
  }

  function showApp() {
    if (authScreen) authScreen.hidden = true;
    if (profileScreen) profileScreen.hidden = true;
    if (pinScreen) pinScreen.hidden = true;
    if (appShell) appShell.hidden = false;
    setTimeout(() => document.querySelector('[data-section="home"]')?.focus(), 0);
  }

  function showProfilePicker() {
    if (!profileScreen || !profileGrid) return;
    if (authScreen) authScreen.hidden = true;
    if (appShell) appShell.hidden = true;
    profileScreen.hidden = false;
    profileGrid.replaceChildren();

    profiles.forEach((profile) => {
      const button = document.createElement("button");
      button.className = "profile-card";
      button.dataset.focusable = "";
      button.dataset.profileId = profile.id;

      const avatar = document.createElement("span");
      avatar.className = "profile-avatar";
      avatar.textContent = String(profile.name || "?").trim().slice(0, 1).toUpperCase();

      const name = document.createElement("strong");
      name.textContent = profile.name || "Profil";

      const type = document.createElement("small");
      type.textContent = profile.profile_type === "child" ? "Enfant" : "Standard";

      button.append(avatar, name, type);
      profileGrid.append(button);
    });

    setProfileStatus("");
    setTimeout(() => profileGrid.querySelector("[data-focusable]")?.focus(), 0);
  }

  async function selectProfile(profileId) {
    const profile = profiles.find((item) => item.id === profileId);
    if (!profile || !currentSession) return;

    currentProfile = profile;
    persistProfileId(profile.id);
    setProfileStatus("Chargement de " + profile.name + "…");

    const [
      loadedFavorites,
      loadedProgress,
      loadedLiveHistory,
      loadedParental,
      loadedLocks,
    ] = await Promise.all([
      window.ZyvioCloud.listFavorites(currentSession, profile.id),
      window.ZyvioCloud.listWatchProgress(currentSession, profile.id, 100),
      window.ZyvioCloud.listLiveHistory(currentSession, profile.id, 50),
      window.ZyvioCloud.getParentalSettings(currentSession),
      window.ZyvioCloud.getProfileContentLocks(currentSession, profile.id),
    ]);

    favorites = loadedFavorites;
    watchProgress = loadedProgress;
    liveHistory = loadedLiveHistory;
    parentalSettings = loadedParental;
    contentLocks = loadedLocks;
    liveChannels = [];
    movies = [];
    series = [];
    episodes = [];
    showApp();
    renderHomeShelves();
    setStatus("Profil : " + profile.name);
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
      renderHomeShelves();
      setStatus(
        currentProfile
          ? "Profil : " + currentProfile.name
          : (currentPlaylist?.name ? "Playlist restaurée : " + currentPlaylist.name : "Accueil")
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

    if (section === "profiles") {
      if (isChildProfile()) {
        showPinPrompt(
          "Changer de profil",
          "Le code PIN parental est requis pour quitter le profil Enfant.",
          async () => showProfilePicker()
        );
      } else {
        showProfilePicker();
      }
      return;
    }

    if (section === "more") {
      if (isChildProfile()) {
        showPinPrompt(
          "Accès protégé",
          "Le code PIN parental est requis pour accéder à cette section.",
          async () => {
            if (accountPanel) accountPanel.hidden = false;
            setStatus("Plus");
            setTimeout(() => accountPanel?.querySelector("[data-focusable]")?.focus(), 0);
          }
        );
        return;
      }
      if (accountPanel) accountPanel.hidden = false;
      setStatus("Plus");
      setTimeout(() => accountPanel?.querySelector("[data-focusable]")?.focus(), 0);
      return;
    }

    if (section === "favorites") {
      loadFavorites();
      return;
    }

    setStatus(section);
  }

  async function restoreProvider(session) {
    setStatus("Synchronisation du compte…");
    await window.ZyvioCloud.ensurePrimaryProfile(session);
    const [restored, accountProfiles] = await Promise.all([
      window.ZyvioCloud.restorePrimaryProvider(session),
      window.ZyvioCloud.listProfiles(session),
    ]);

    currentPlaylist = restored.playlist;
    providerConfig = restored.providerConfig;
    profiles = accountProfiles;
    currentProfile = null;
    favorites = [];
    watchProgress = [];
    liveHistory = [];
    liveChannels = [];
    movies = [];
    series = [];
    episodes = [];

    if (!providerConfig) {
      showApp();
      setStatus("Aucune playlist active configurée sur ce compte.");
      return;
    }

    const preferredId = storedProfileId();
    const preferred = profiles.find((item) => item.id === preferredId);
    if (preferred) {
      await selectProfile(preferred.id);
    } else if (profiles.length === 1) {
      await selectProfile(profiles[0].id);
    } else {
      showProfilePicker();
    }

    setStatus(
      currentProfile
        ? "Profil : " + currentProfile.name
        : (currentPlaylist?.name ? "Playlist restaurée : " + currentPlaylist.name : "Playlist restaurée.")
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
      liveChannels = filterForProfile("live", await window.ZyvioProvider.loadLive(providerConfig));
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
      if (entry.favoriteType) button.dataset.favoriteType = entry.favoriteType;
      if (["movie", "series", "live"].includes(kind) && isFavorite(kind, entry.id)) {
        button.classList.add("favorite");
      }

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
      movies = filterForProfile("movie", await window.ZyvioProvider.loadMovies(providerConfig));
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
      series = filterForProfile("series", await window.ZyvioProvider.loadSeries(providerConfig));
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

  async function loadFavorites() {
    if (!currentSession || !currentProfile) return;
    try {
      favorites = await window.ZyvioCloud.listFavorites(currentSession, currentProfile.id);
      const visibleFavorites = favorites.filter((item) => {
        if (isLibraryItemLocked(item)) return false;
        if (isChildProfile() && item.content_type === "live") {
          return liveChannels.some((channel) => String(channel.id) === String(item.content_id));
        }
        return true;
      });
      const entries = visibleFavorites.slice(0, 80).map((item) => ({
        id: item.content_id,
        title: item.title,
        categoryName: item.content_type === "movie" ? "Film" :
          item.content_type === "series" ? "Série" : "TV",
        favoriteType: item.content_type,
      }));
      renderCatalog("Favoris", entries, "favorite");
      setStatus(entries.length ? entries.length + " favori(s)." : "Aucun favori.");
    } catch (_) {
      setStatus("Impossible de charger les favoris.");
    }
  }

  function clearHomeContainer(container) {
    if (container) container.replaceChildren();
  }

  function createHomeCard(item, kind, subtitle, progressFraction = null) {
    const button = document.createElement("button");
    button.className = "card home-item";
    button.dataset.focusable = "";
    button.dataset.homeKind = kind;
    button.dataset.homeId = String(item.id || item.content_id || "");
    button.dataset.homeType = String(item.content_type || (kind === "recent-live" ? "live" : ""));
    button.dataset.homePlaylist = String(item.playlist_id || currentPlaylist?.id || "");

    const title = document.createElement("span");
    title.textContent = item.title || "Contenu";
    button.append(title);

    if (subtitle) {
      const small = document.createElement("small");
      small.textContent = subtitle;
      button.append(small);
    }

    if (progressFraction !== null) {
      const bar = document.createElement("span");
      bar.className = "home-progress";
      const fill = document.createElement("span");
      fill.style.width = Math.round(Math.max(0, Math.min(1, progressFraction)) * 100) + "%";
      bar.append(fill);
      button.append(bar);
    }
    return button;
  }

  function renderHomeShelves() {
    const resumable = watchProgress
      .filter((item) =>
        !item.completed &&
        Number(item.position_ms || 0) >= 10_000 &&
        !isLibraryItemLocked(item)
      )
      .slice(0, 20);
    clearHomeContainer(continueCards);
    resumable.forEach((item) => {
      const duration = Number(item.duration_ms || 0);
      const fraction = duration > 0 ? Number(item.position_ms || 0) / duration : 0;
      const label = item.content_type === "episode" && item.season_number != null
        ? "S" + item.season_number + " · E" + item.episode_number
        : "Film";
      continueCards?.append(createHomeCard(item, "continue", label, fraction));
    });
    if (continueShelf) continueShelf.hidden = resumable.length === 0;

    const recentChannels = liveHistory
      .filter((item) => !isChildProfile() || liveChannels.some(
        (channel) => String(channel.id) === String(item.channel_id)
      ))
      .slice(0, 20);
    clearHomeContainer(recentChannelCards);
    recentChannels.forEach((item) => {
      recentChannelCards?.append(createHomeCard(
        {
          id: item.channel_id,
          title: item.channel_name,
          content_type: "live",
          playlist_id: item.playlist_id,
        },
        "recent-live",
        "TV en direct"
      ));
    });
    if (recentChannelsShelf) recentChannelsShelf.hidden = recentChannels.length === 0;

    const favoriteItems = favorites
      .filter((item) => {
        if (isLibraryItemLocked(item)) return false;
        if (isChildProfile() && item.content_type === "live") {
          return liveChannels.some((channel) => String(channel.id) === String(item.content_id));
        }
        return true;
      })
      .slice(0, 20);
    clearHomeContainer(favoriteCards);
    favoriteItems.forEach((item) => {
      favoriteCards?.append(createHomeCard(
        item,
        "favorite",
        item.content_type === "movie" ? "Film" :
          item.content_type === "series" ? "Série" : "TV"
      ));
    });
    if (favoritesShelf) favoritesShelf.hidden = favoriteItems.length === 0;

    const recent = watchProgress
      .filter((item) => !isLibraryItemLocked(item))
      .slice(0, 20);
    clearHomeContainer(historyCards);
    recent.forEach((item) => {
      historyCards?.append(createHomeCard(
        item,
        "history",
        item.content_type === "episode" && item.season_number != null
          ? "S" + item.season_number + " · E" + item.episode_number
          : "Film"
      ));
    });
    if (historyShelf) historyShelf.hidden = recent.length === 0;
  }

  async function syncActivePlayback() {
    if (!activePlayback || !currentSession || !currentProfile || !currentPlaylist) return;
    if (activePlayback.trackProgress === false) return;

    const positionMs = player.getPositionMs();
    const durationMs = player.getDurationMs();
    if (positionMs <= 0) return;

    const completed = durationMs > 0 && positionMs >= durationMs * 0.95;
    const payload = {
      playlistId: currentPlaylist.id,
      contentType: activePlayback.contentType,
      contentId: activePlayback.contentId,
      title: activePlayback.title,
      seriesId: activePlayback.seriesId || null,
      seasonNumber: activePlayback.seasonNumber ?? null,
      episodeNumber: activePlayback.episodeNumber ?? null,
      artworkUrl: activePlayback.artworkUrl || null,
      positionMs,
      durationMs: durationMs || null,
      completed,
    };

    try {
      await window.ZyvioCloud.upsertWatchProgress(currentSession, currentProfile.id, payload);
      watchProgress = watchProgress.filter(
        (item) => libraryKey(item.playlist_id, item.content_type, item.content_id) !==
          libraryKey(payload.playlistId, payload.contentType, payload.contentId)
      );
      watchProgress.unshift({
        playlist_id: payload.playlistId,
        content_type: payload.contentType,
        content_id: payload.contentId,
        title: payload.title,
        series_id: payload.seriesId,
        season_number: payload.seasonNumber,
        episode_number: payload.episodeNumber,
        artwork_url: payload.artworkUrl,
        position_ms: payload.positionMs,
        duration_ms: payload.durationMs,
        completed: payload.completed,
        last_watched_at: new Date().toISOString(),
      });
      renderHomeShelves();
    } catch (_) {}
  }

  async function startTrackedPlayback(streamUrl, metadata) {
    await syncActivePlayback();
    await player.play(streamUrl);
    activePlayback = {
      contentType: metadata.contentType,
      contentId: metadata.contentId,
      title: metadata.title,
      seriesId: metadata.seriesId || null,
      seasonNumber: metadata.seasonNumber ?? null,
      episodeNumber: metadata.episodeNumber ?? null,
      artworkUrl: metadata.artworkUrl || null,
      trackProgress: true,
    };

    const previous = progressFor(metadata.contentType, metadata.contentId);
    const resumeMs = Number(previous?.position_ms || 0);
    if (!previous?.completed && resumeMs >= 10_000) {
      player.seekToMs(resumeMs);
      setStatus("Reprise : " + metadata.title);
    } else {
      setStatus("Lecture : " + metadata.title);
    }
  }

  async function resolveProgressPlayback(progress) {
    if (progress.content_type === "movie") {
      if (!movies.length) movies = filterForProfile("movie", await window.ZyvioProvider.loadMovies(providerConfig));
      const movie = movies.find((item) => item.id === String(progress.content_id));
      if (!movie) throw new Error("Film introuvable.");
      return startTrackedPlayback(movie.streamUrl, {
        contentType: "movie",
        contentId: movie.id,
        title: movie.title,
        artworkUrl: movie.poster || null,
      });
    }

    if (progress.content_type === "episode" && progress.series_id) {
      if (isLibraryItemLocked(progress)) throw new Error("Contenu bloqué.");
      const detail = await window.ZyvioProvider.loadXtreamSeriesInfo(
        providerConfig,
        String(progress.series_id)
      );
      const episode = Object.values(detail.episodesBySeason || {})
        .flat()
        .find((item) => item.id === String(progress.content_id));
      if (!episode) throw new Error("Épisode introuvable.");
      return startTrackedPlayback(episode.streamUrl, {
        contentType: "episode",
        contentId: episode.id,
        title: progress.title,
        seriesId: String(progress.series_id),
        seasonNumber: progress.season_number,
        episodeNumber: progress.episode_number,
        artworkUrl: progress.artwork_url || null,
      });
    }

    throw new Error("Contenu indisponible.");
  }

  async function toggleFavoriteForFocused() {
    if (!currentSession || !currentProfile || !currentPlaylist) return;
    const target = document.activeElement?.closest?.("[data-catalog-kind], [data-channel-id]");
    let kind = target?.dataset.catalogKind || "";
    let source = null;

    if (kind === "movie") {
      source = movies.find((item) => item.id === target.dataset.catalogId);
    } else if (kind === "series") {
      source = series.find((item) => item.id === target.dataset.catalogId);
    } else if (target?.dataset.channelId) {
      kind = "live";
      source = liveChannels.find((item) => item.id === target.dataset.channelId);
    } else if (activePlayback?.contentType === "live") {
      kind = "live";
      source = liveChannels.find((item) => item.id === activePlayback.contentId);
    }

    if (!source || !["movie", "series", "live"].includes(kind)) return;

    const payload = {
      playlistId: currentPlaylist.id,
      contentType: kind,
      contentId: source.id,
      title: kind === "live" ? source.name : source.title,
      artworkUrl: kind === "live" ? (source.logo || null) : (source.poster || null),
    };

    try {
      if (isFavorite(kind, source.id)) {
        await window.ZyvioCloud.removeFavorite(currentSession, currentProfile.id, payload);
        favorites = favorites.filter((item) => favoriteKey(item) !== favoriteKey(kind, source.id));
        setStatus(payload.title + " retiré des favoris.");
      } else {
        await window.ZyvioCloud.upsertFavorite(currentSession, currentProfile.id, payload);
        favorites.unshift({
          playlist_id: payload.playlistId,
          content_type: payload.contentType,
          content_id: payload.contentId,
          title: payload.title,
          artwork_url: payload.artworkUrl,
        });
        setStatus(payload.title + " ajouté aux favoris.");
      }
      renderHomeShelves();
      if (target) target.classList.toggle("favorite", isFavorite(kind, source.id));
    } catch (_) {
      setStatus("Impossible de synchroniser le favori.");
    }
  }

  async function playCatalogStream(streamUrl, metadata) {
    try {
      await startTrackedPlayback(streamUrl, metadata);
    } catch (_) {
      activePlayback = null;
      setStatus("Contenu indisponible.");
    }
  }

  async function playChannel(channel) {
    if (!channel) return;
    try {
      await syncActivePlayback();
      await player.play(channel.streamUrl);
      activePlayback = {
        contentType: "live",
        contentId: channel.id,
        title: channel.name,
        artworkUrl: channel.logo || null,
        trackProgress: false,
      };
      try {
        await window.ZyvioCloud.recordLiveHistory(currentSession, currentProfile.id, {
          playlistId: currentPlaylist.id,
          channelId: channel.id,
          channelName: channel.name,
          logoUrl: channel.logo || null,
        });
        liveHistory = liveHistory.filter(
          (item) => libraryKey(item.playlist_id, "live", item.channel_id) !==
            libraryKey(currentPlaylist.id, "live", channel.id)
        );
        liveHistory.unshift({
          playlist_id: currentPlaylist.id,
          channel_id: String(channel.id),
          channel_name: channel.name,
          logo_url: channel.logo || null,
          last_watched_at: new Date().toISOString(),
        });
        renderHomeShelves();
      } catch (_) {}
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

  async function stopPlayback() {
    if (!activePlayback && !player?.currentUrl) return false;
    try { await syncActivePlayback(); } catch (_) {}
    player?.stop();
    activePlayback = null;
    setStatus("Lecture arrêtée.");
    setTimeout(() => document.querySelector('[data-section="home"]')?.focus(), 0);
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

  document.addEventListener("keydown", async (event) => {
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

    if (pinScreen && !pinScreen.hidden && /^\d$/.test(event.key) && document.activeElement === pinInput) {
      pinInput.value = (pinInput.value + event.key).replace(/\D/g, "").slice(0, 4);
      event.preventDefault();
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
      await stopPlayback();
      return;
    }

    if (event.key === "ColorF0Red" || event.keyCode === 403) {
      event.preventDefault();
      await toggleFavoriteForFocused();
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

      if (pinScreen && !pinScreen.hidden) {
        closePinPrompt();
        return;
      }

      if (await stopPlayback()) return;

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

      if (profileScreen && !profileScreen.hidden) {
        if (currentProfile && isChildProfile()) {
          showPinPrompt(
            "Retour au profil Enfant",
            "Le code PIN parental est requis pour quitter le profil Enfant.",
            async () => {
              profileScreen.hidden = true;
              showApp();
              renderHomeShelves();
            }
          );
        } else if (currentProfile) {
          profileScreen.hidden = true;
          showApp();
          renderHomeShelves();
        } else {
          exitApp();
        }
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
    const target = event.target.closest("[data-section], [data-action], [data-channel-id], [data-catalog-kind], [data-profile-id], [data-home-kind]");
    if (!target) return;

    if (target.dataset.profileId) {
      try {
        await selectProfile(target.dataset.profileId);
      } catch (_) {
        setProfileStatus("Impossible de charger ce profil.");
      }
      return;
    }

    if (target.dataset.homeKind === "continue" || target.dataset.homeKind === "history") {
      const progressKey = libraryKey(
        target.dataset.homePlaylist,
        target.dataset.homeType,
        target.dataset.homeId
      );
      const progress = watchProgress.find(
        (item) => libraryKey(item.playlist_id, item.content_type, item.content_id) === progressKey
      );
      if (progress) {
        try { await resolveProgressPlayback(progress); }
        catch (_) { setStatus("Contenu indisponible."); }
      }
      return;
    }

    if (target.dataset.homeKind === "recent-live") {
      if (!liveChannels.length) await loadProviderLive();
      const channel = liveChannels.find(
        (item) => String(item.id) === String(target.dataset.homeId)
      );
      if (channel) await playChannel(channel);
      else setStatus("Chaîne indisponible.");
      return;
    }

    if (target.dataset.homeKind === "favorite") {
      const favoriteHomeKey = libraryKey(
        target.dataset.homePlaylist,
        target.dataset.homeType,
        target.dataset.homeId
      );
      const item = favorites.find(
        (entry) => libraryKey(entry.playlist_id, entry.content_type, entry.content_id) === favoriteHomeKey
      );
      if (!item) return;

      if (item.content_type === "movie") {
        if (!movies.length) movies = filterForProfile("movie", await window.ZyvioProvider.loadMovies(providerConfig));
        const movie = movies.find((entry) => entry.id === String(item.content_id));
        if (movie) {
          await playCatalogStream(movie.streamUrl, {
            contentType: "movie",
            contentId: movie.id,
            title: movie.title,
            artworkUrl: movie.poster || null,
          });
        }
      } else if (item.content_type === "series") {
        if (!series.length) series = filterForProfile("series", await window.ZyvioProvider.loadSeries(providerConfig));
        const seriesItem = series.find((entry) => entry.id === String(item.content_id));
        if (seriesItem) {
          try {
            const info = await window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, seriesItem.id);
            renderEpisodes(seriesItem, info);
          } catch (_) {
            setStatus("Détails de série indisponibles.");
          }
        }
      } else if (item.content_type === "live") {
        if (!liveChannels.length) await loadProviderLive();
        const channel = liveChannels.find((entry) => entry.id === String(item.content_id));
        if (channel) await playChannel(channel);
      }
      return;
    }

    if (target.dataset.channelId) {
      const channel = liveChannels.find(
        (item) => String(item.id) === String(target.dataset.channelId)
      );
      await playChannel(channel);
      return;
    }

    if (target.dataset.catalogKind === "favorite") {
      const type = target.dataset.favoriteType;
      const id = target.dataset.catalogId;

      if (type === "movie") {
        if (!movies.length) movies = filterForProfile("movie", await window.ZyvioProvider.loadMovies(providerConfig));
        const movie = movies.find((item) => item.id === id);
        if (movie) {
          await playCatalogStream(movie.streamUrl, {
            contentType: "movie",
            contentId: movie.id,
            title: movie.title,
            artworkUrl: movie.poster || null,
          });
        }
      } else if (type === "series") {
        if (!series.length) series = filterForProfile("series", await window.ZyvioProvider.loadSeries(providerConfig));
        const seriesItem = series.find((item) => item.id === id);
        if (seriesItem) {
          try {
            const info = await window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, seriesItem.id);
            renderEpisodes(seriesItem, info);
          } catch (_) {
            setStatus("Détails de série indisponibles.");
          }
        }
      } else if (type === "live") {
        if (!liveChannels.length) await loadProviderLive();
        const channel = liveChannels.find((item) => item.id === id);
        if (channel) await playChannel(channel);
      }
      return;
    }

    if (target.dataset.catalogKind === "movie") {
      const movie = movies.find((item) => item.id === target.dataset.catalogId);
      if (movie) {
        await playCatalogStream(movie.streamUrl, {
          contentType: "movie",
          contentId: movie.id,
          title: movie.title,
          artworkUrl: movie.poster || null,
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
          contentType: "episode",
          contentId: episode.id,
          title: episode.seriesTitle + " — S" + episode.season + "E" + episode.number,
          seriesId: episode.seriesId,
          seasonNumber: episode.season,
          episodeNumber: episode.number,
        });
      }
      return;
    }

    if (target.dataset.action === "verify-pin") {
      await verifyPendingPin();
      return;
    }

    if (target.dataset.action === "cancel-pin") {
      closePinPrompt();
      return;
    }

    if (target.dataset.action === "sign-in") {
      await signIn();
      return;
    }

    if (target.dataset.action === "sign-out") {
      if (isChildProfile()) {
        showPinPrompt(
          "Déconnexion protégée",
          "Le code PIN parental est requis pour quitter le profil Enfant.",
          async () => signOut()
        );
      } else {
        await signOut();
      }
      return;
    }

    if (target.dataset.section) {
      activate(target.dataset.section);
    }
  });

  window.addEventListener("load", () => {
    restoreAccount();
    setInterval(() => {
      syncActivePlayback();
    }, 30_000);
  });
})();