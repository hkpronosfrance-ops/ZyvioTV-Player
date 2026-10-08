(() => {
    "use strict";

    const focusableSelector = "[data-focusable]";
    const status = document.getElementById("status");
    const authStatus = document.getElementById("auth-status");
    const authScreen = document.getElementById("auth-screen");
    const appShell = document.getElementById("app-shell");
    const profileScreen = document.getElementById("profile-screen");
    const systemScreen = document.getElementById("system-screen");
    const systemTitle = document.getElementById("system-title");
    const systemCopy = document.getElementById("system-copy");
    const systemContinue = document.getElementById("system-continue");
    const systemSignout = document.getElementById("system-signout");
    const deviceEditScreen = document.getElementById("device-edit-screen");
    const deviceNameInput = document.getElementById("device-name-input");
    const deviceEditStatus = document.getElementById("device-edit-status");
    const morePanel = document.getElementById("more-panel");
    const devicesPanel = document.getElementById("devices-panel");
    const devicesGrid = document.getElementById("devices-grid");
    const devicesCount = document.getElementById("devices-count");
    const profileGrid = document.getElementById("profile-grid");
    const profileStatus = document.getElementById("profile-status");
    const pinScreen = document.getElementById("pin-screen");
    const pinTitle = document.getElementById("pin-title");
    const pinCopy = document.getElementById("pin-copy");
    const pinInput = document.getElementById("pin-input");
    const pinStatus = document.getElementById("pin-status");
    const continueShelf = document.getElementById("continue-shelf");
    const continueCards = document.getElementById("continue-cards");
    const nextEpisodesShelf = document.getElementById("next-episodes-shelf");
    const nextEpisodeCards = document.getElementById("next-episode-cards");
    const recentChannelsShelf = document.getElementById("recent-channels-shelf");
    const recentChannelCards = document.getElementById("recent-channel-cards");
    const favoritesShelf = document.getElementById("favorites-shelf");
    const favoriteCards = document.getElementById("favorite-cards");
    const recentMoviesShelf = document.getElementById("recent-movies-shelf");
    const recentMovieCards = document.getElementById("recent-movie-cards");
    const recentSeriesShelf = document.getElementById("recent-series-shelf");
    const recentSeriesCards = document.getElementById("recent-series-cards");
    const sameCategoryShelf = document.getElementById("same-category-shelf");
    const sameCategoryCards = document.getElementById("same-category-cards");
    const sameCategoryTitle = document.getElementById("same-category-title");
    const catalogPanel = document.getElementById("catalog-panel");
    const catalogTitle = document.getElementById("catalog-title");
    const catalogCount = document.getElementById("catalog-count");
    const catalogGrid = document.getElementById("catalog-grid");
    const emailInput = document.getElementById("auth-email");
    const passwordInput = document.getElementById("auth-password");
    const video = document.getElementById("tv-player");
    const player = window.ZyvioPlayer?.create(video);
    let providerConfig = null;
    let liveChannels = [];
    let movies = [];
    let series = [];
    let episodes = [];
    let currentSession = null;
    let currentProfile = null;
    let profiles = [];
    let currentPlaylist = null;
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
    let activePlayback = null;
    let devices = [];
    let systemState = { type: "normal", message: null, blocking: false };
    let plannedMaintenanceDismissed = false;
    let editingDeviceId = null;
    let nextEpisodes = [];
    let homeRefreshToken = 0;
    let lastHomeFocus = null;

    const PROFILE_STORAGE_KEY = "zyviotv.tizen.profile.v1";
    const DEVICE_UID_STORAGE_KEY = "zyviotv.tizen.device_uid.v1";
    const RUNTIME_CACHE_PREFIX = "zyviotv.tizen.parental_runtime.v1.";

    function stableHash(value) {
        const text = String(value || "");
        const seeds = [2166136261, 2246822519, 3266489917, 668265263];
        return seeds.map((seed, index) => {
            let hash = seed >>> 0;
            for (let i = 0; i < text.length; i += 1) {
                hash ^= text.charCodeAt(i) + index * 17;
                hash = Math.imul(hash, 16777619) >>> 0;
                hash ^= hash >>> 13;
            }
            return hash.toString(16).padStart(8, "0");
        }).join("");
    }

    function hardwareDerivedDeviceUid() {
        try {
            const rawDuid = String(window.webapis?.productinfo?.getDuid?.() || "").trim();
            if (!rawDuid) return null;
            return "tizen-duid-" + stableHash("zyviotv-player:" + rawDuid);
        } catch (_) {
            return null;
        }
    }

    function randomDeviceUid() {
        const random = window.crypto?.getRandomValues
            ? Array.from(window.crypto.getRandomValues(new Uint8Array(16)))
                .map((value) => value.toString(16).padStart(2, "0"))
                .join("")
            : Math.random().toString(36).slice(2) + Date.now().toString(36);
        return "tizen-" + random.slice(0, 48);
    }

    function stableDeviceUid() {
        const hardwareUid = hardwareDerivedDeviceUid();

        try {
            const existing = localStorage.getItem(DEVICE_UID_STORAGE_KEY);
            if (hardwareUid) {
                if (existing !== hardwareUid) {
                    localStorage.setItem(DEVICE_UID_STORAGE_KEY, hardwareUid);
                }
                return hardwareUid;
            }
            if (existing) return existing;

            const uid = randomDeviceUid();
            localStorage.setItem(DEVICE_UID_STORAGE_KEY, uid);
            return uid;
        } catch (_) {
            return hardwareUid || randomDeviceUid();
        }
    }

    const deviceUid = stableDeviceUid();

    function tizenAppVersion() {
        try {
            return window.tizen?.application?.getAppInfo?.().version || "0.1.0";
        } catch (_) {
            return "0.1.0";
        }
    }

    function tizenDeviceName() {
        try {
            const model = window.webapis?.productinfo?.getModel?.();
            if (model) return "Samsung TV " + model;
        } catch (_) {}
        return "Samsung TV";
    }

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

    function trustedRuntimeNowMs(cache = parentalRuntime) {
        if (!cache) return 0;

        const currentPerf = Number(performance.now() || 0);
        const anchorPerf = Number(cache.trustedPerformanceMs || 0);
        if (anchorPerf > 0 && currentPerf >= anchorPerf) {
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

    function setPinStatus(message) {
        if (pinStatus) pinStatus.textContent = message || "";
    }

    function isChildProfile() {
        return currentProfile?.profile_type === "child";
    }

    function normalizeAdultLabel(value) {
        return String(value || "")
            .toLowerCase()
            .normalize?.("NFD")
            .replace?.(/[\u0300-\u036f]/g, "") || String(value || "").toLowerCase();
    }

    function isAdultCategoryLabel(value) {
        const normalized = normalizeAdultLabel(value);
        return ["adult", "adulte", "xxx", "porn", "erotic", "erotique", "18+", "+18"]
            .some((token) => normalized.includes(token));
    }

    function contentKey(kind, id) {
        return String(kind) + ":" + String(id);
    }

    function categoryKey(kind, id) {
        return String(kind) + ":" + String(id);
    }

    function isLockedItem(kind, item) {
        if (!isChildProfile()) return false;
        if (isAdultCategoryLabel(item.categoryName || "")) return true;
        if (!contentLocks?.parental_enabled) return false;

        const lockedContent = new Set(contentLocks.locked_content_keys || []);
        const lockedCategories = new Set(contentLocks.locked_category_keys || []);
        const itemContentKey = contentKey(kind, item.id);
        const itemCategoryKey = categoryKey(kind, item.categoryId || "");

        if (lockedContent.has(itemContentKey)) return true;
        if (item.categoryId && lockedCategories.has(itemCategoryKey)) return true;
        return false;
    }

    function filterForProfile(kind, items) {
        if (!isChildProfile()) return items;
        return items.filter((item) => !isLockedItem(kind, item));
    }

    function showPinPrompt(title, copy, action, verifier = null) {
        pendingPinAction = action;
        pendingPinVerifier = verifier;
        if (pinTitle) pinTitle.textContent = title || "Code PIN requis";
        if (pinCopy) pinCopy.textContent = copy || "Saisissez le code PIN parental à 4 chiffres.";
        if (pinInput) pinInput.value = "";
        setPinStatus("");
        if (pinScreen) pinScreen.hidden = false;
        setTimeout(() => pinInput?.focus(), 0);
    }

    function closePinPrompt() {
        pendingPinAction = null;
        pendingPinVerifier = null;
        if (pinInput) pinInput.value = "";
        setPinStatus("");
        if (pinScreen) pinScreen.hidden = true;
    }

    async function verifyPendingPin() {
        const pin = String(pinInput?.value || "").replace(/\D/g, "").slice(0, 4);
        if (pin.length !== 4) {
            setPinStatus("Saisissez exactement 4 chiffres.");
            return;
        }

        setPinStatus("Vérification…");
        try {
            if (typeof pendingPinVerifier === "function") {
                const verification = await pendingPinVerifier(pin);
                if (verification?.ok) {
                    const action = pendingPinAction;
                    closePinPrompt();
                    if (typeof action === "function") await action();
                    return;
                }
                setPinStatus(verification?.message || "Code PIN non vérifié.");
                return;
            }

            const result = await window.ZyvioCloud.verifyParentalPin(currentSession, pin);
            if (result?.verified) {
                const action = pendingPinAction;
                closePinPrompt();
                if (typeof action === "function") await action();
                return;
            }

            if (result?.reason === "blocked") {
                setPinStatus("Trop de tentatives. Réessayez dans quelques minutes.");
            } else if (result?.reason === "pin_not_configured") {
                setPinStatus("Aucun code PIN parental n’est configuré.");
            } else {
                const remaining = result?.attempts_remaining;
                setPinStatus(
                    remaining == null
                        ? "Code PIN incorrect."
                        : "Code PIN incorrect. " + remaining + " tentative(s) restante(s)."
                );
            }
        } catch (_) {
            setPinStatus("Impossible de vérifier le code PIN.");
        }
    }

    function setProfileStatus(message) {
        if (profileStatus) profileStatus.textContent = message || "";
    }

    function showProfilePicker() {
        if (!profileScreen || !profileGrid) return;
        if (appShell) appShell.hidden = true;
        if (authScreen) authScreen.hidden = true;
        profileScreen.hidden = false;
        profileGrid.replaceChildren();

        profiles.forEach((profile) => {
            const button = document.createElement("button");
            button.className = "profile-card";
            button.dataset.focusable = "";
            button.dataset.profileId = profile.id;

            const avatar = document.createElement("span");
            avatar.className = "profile-avatar";
            avatar.textContent = String(profile.name || "P").trim().charAt(0).toUpperCase() || "P";

            const name = document.createElement("strong");
            name.textContent = profile.name || "Profil";

            const type = document.createElement("small");
            type.textContent = profile.profile_type === "child" ? "Enfant" : "Standard";

            button.append(avatar, name, type);
            profileGrid.append(button);
        });

        setProfileStatus(profiles.length ? "Choisissez votre profil." : "Aucun profil disponible.");
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
            loadedRuntime,
        ] = await Promise.all([
            window.ZyvioCloud.listFavorites(currentSession, profile.id),
            window.ZyvioCloud.listWatchProgress(currentSession, profile.id, 100),
            window.ZyvioCloud.listLiveHistory(currentSession, profile.id, 50),
            window.ZyvioCloud.getParentalSettings(currentSession),
            window.ZyvioCloud.getProfileContentLocks(currentSession, profile.id),
            window.ZyvioCloud.getParentalRuntimeState(currentSession, profile.id, "")
                .catch(() => null),
        ]);
        favorites = loadedFavorites;
        watchProgress = loadedProgress;
        liveHistory = loadedLiveHistory;
        parentalSettings = loadedParental;
        contentLocks = loadedLocks;
        if (loadedRuntime) {
            saveRuntimeCache(profile.id, loadedRuntime);
        } else {
            parentalRuntime = loadRuntimeCache(profile.id);
        }
        runtimeExceptionUntilMs = Number(loadedRuntime?.exception_until_epoch_ms || 0);
        runtimeBlocked = false;

        if (profileScreen) profileScreen.hidden = true;
        showApp();
        renderHomeShelves();
        setStatus("Profil : " + profile.name);
    }

    function setAuthStatus(message) {
        if (authStatus) authStatus.textContent = message || "";
    }

    function showAuth() {
        if (authScreen) authScreen.hidden = false;
        if (profileScreen) profileScreen.hidden = true;
        if (systemScreen) systemScreen.hidden = true;
        if (appShell) appShell.hidden = true;
        providerConfig = null;
        liveChannels = [];
        player?.stop();
        setTimeout(() => emailInput?.focus(), 0);
    }

    function showApp() {
        if (authScreen) authScreen.hidden = true;
        if (profileScreen) profileScreen.hidden = true;
        if (appShell) appShell.hidden = false;
        setTimeout(() => focusables()[0]?.focus(), 0);
    }

    function hideSecondaryPanels() {
        if (morePanel) morePanel.hidden = true;
        if (devicesPanel) devicesPanel.hidden = true;
    }

    function showSystemState(state) {
        systemState = state || { type: "normal", message: null, blocking: false };
        if (!systemScreen) return true;

        if (systemState.type === "normal") {
            systemScreen.hidden = true;
            return true;
        }

        if (systemState.type === "maintenance_planned" && plannedMaintenanceDismissed) {
            systemScreen.hidden = true;
            return true;
        }

        if (systemTitle) {
            systemTitle.textContent =
                systemState.type === "account_suspended" ? "Compte suspendu" :
                systemState.type === "maintenance_blocking" ? "Maintenance en cours" :
                "Maintenance programmée";
        }
        if (systemCopy) {
            systemCopy.textContent = systemState.message ||
                (systemState.type === "account_suspended"
                    ? "L’accès au service est actuellement suspendu pour ce compte."
                    : systemState.type === "maintenance_blocking"
                        ? "Le service est momentanément indisponible pendant la maintenance."
                        : "Une maintenance est prévue prochainement. Vous pouvez continuer à utiliser ZYVIOTV.");
        }
        if (systemContinue) systemContinue.hidden = systemState.type !== "maintenance_planned";
        if (systemSignout) systemSignout.hidden = !systemState.blocking;

        systemScreen.hidden = false;
        setTimeout(() => systemScreen.querySelector("[data-focusable]:not([hidden])")?.focus(), 0);
        return !systemState.blocking;
    }

    async function refreshSystemState() {
        if (!currentSession) return true;
        try {
            const state = await window.ZyvioCloud.getSystemState(currentSession);
            return showSystemState(state);
        } catch (_) {
            return true;
        }
    }

    async function registerCurrentDevice() {
        if (!currentSession) return;
        try {
            await window.ZyvioCloud.registerDevice(currentSession, {
                deviceUid,
                displayName: tizenDeviceName(),
                appVersion: tizenAppVersion(),
            });
        } catch (_) {}
    }

    function renderDevices() {
        if (!devicesGrid || !devicesPanel) return;
        devicesPanel.hidden = false;
        if (morePanel) morePanel.hidden = true;
        if (catalogPanel) catalogPanel.hidden = true;
        if (devicesCount) {
            devicesCount.textContent = devices.length + " appareil" + (devices.length > 1 ? "s" : "");
        }
        devicesGrid.replaceChildren();

        devices.forEach((device) => {
            const button = document.createElement("button");
            button.className = "catalog-card device-card";
            button.dataset.focusable = "";
            button.dataset.deviceId = device.id;
            if (device.device_uid === deviceUid) button.classList.add("current");

            const strong = document.createElement("strong");
            strong.textContent = device.display_name || "Appareil";

            const small = document.createElement("small");
            const platform = device.platform === "tizen" ? "Samsung TV" : device.platform;
            small.textContent = platform + (device.app_version ? " · v" + device.app_version : "");

            button.append(strong, small);
            devicesGrid.append(button);
        });

        setStatus("Touche rouge : déconnecter un autre appareil.");
        setTimeout(() => devicesGrid.querySelector("[data-focusable]")?.focus(), 0);
    }

    function setDeviceEditStatus(message) {
        if (deviceEditStatus) deviceEditStatus.textContent = message || "";
    }

    function openDeviceRename(device) {
        if (!deviceEditScreen || !deviceNameInput || !device) return;
        editingDeviceId = device.id;
        deviceNameInput.value = device.display_name || "";
        setDeviceEditStatus("");
        deviceEditScreen.hidden = false;
        setTimeout(() => deviceNameInput.focus(), 0);
    }

    function closeDeviceRename() {
        editingDeviceId = null;
        if (deviceNameInput) deviceNameInput.value = "";
        setDeviceEditStatus("");
        if (deviceEditScreen) deviceEditScreen.hidden = true;
    }

    async function saveDeviceRename() {
        if (!editingDeviceId || !currentSession) return;
        const clean = String(deviceNameInput?.value || "").trim();
        if (!clean) {
            setDeviceEditStatus("Le nom de l’appareil ne peut pas être vide.");
            return;
        }
        setDeviceEditStatus("Enregistrement…");
        try {
            await window.ZyvioCloud.renameDevice(currentSession, editingDeviceId, clean);
            devices = devices.map((item) =>
                item.id === editingDeviceId ? { ...item, display_name: clean } : item
            );
            closeDeviceRename();
            renderDevices();
            setStatus("Appareil renommé.");
        } catch (_) {
            setDeviceEditStatus("Impossible de renommer cet appareil.");
        }
    }

    async function loadDevices() {
        if (!currentSession) return;
        setStatus("Chargement des appareils…");
        try {
            devices = await window.ZyvioCloud.listDevices(currentSession);
            renderDevices();
        } catch (_) {
            setStatus("Impossible de charger les appareils.");
        }
    }

    async function disconnectFocusedDevice() {
        const target = document.activeElement?.closest?.("[data-device-id]");
        if (!target || !currentSession) return;
        const device = devices.find((item) => item.id === target.dataset.deviceId);
        if (!device) return;
        if (device.device_uid === deviceUid) {
            setStatus("Impossible de déconnecter cet appareil depuis lui-même.");
            return;
        }
        try {
            await window.ZyvioCloud.deleteDevice(currentSession, device.id);
            devices = devices.filter((item) => item.id !== device.id);
            renderDevices();
            setStatus("Appareil déconnecté.");
        } catch (_) {
            setStatus("Impossible de déconnecter cet appareil.");
        }
    }

    async function restoreProviderFromAccount(session) {
        setStatus("Synchronisation du compte…");
        await window.ZyvioCloud.ensurePrimaryProfile(session);
        const [restored, accountProfiles] = await Promise.all([
            window.ZyvioCloud.restorePrimaryProvider(session),
            window.ZyvioCloud.listProfiles(session),
        ]);
        providerConfig = restored.providerConfig;
        currentPlaylist = restored.playlist;
        profiles = accountProfiles;
        currentProfile = null;
        liveChannels = [];
        movies = [];
        series = [];
        episodes = [];
        favorites = [];
        watchProgress = [];
        liveHistory = [];

        const preferredId = storedProfileId();
        const preferred = profiles.find((item) => item.id === preferredId);
        if (preferred) {
            await selectProfile(preferred.id);
        } else if (profiles.length === 1) {
            await selectProfile(profiles[0].id);
        } else {
            showProfilePicker();
        }

        if (!providerConfig) {
            setStatus("Aucune playlist active configurée sur ce compte.");
            return;
        }

        setStatus(
            restored.playlist?.name
                ? "Playlist restaurée : " + restored.playlist.name
                : "Playlist restaurée."
        );
    }

    async function restoreAccount() {
        setAuthStatus("Restauration de la session…");
        try {
            currentSession = await window.ZyvioAuth.restoreSession();
            if (!currentSession) {
                setAuthStatus("");
                showAuth();
                return;
            }
            await registerCurrentDevice();
            const allowed = await refreshSystemState();
            if (!allowed) return;
            await restoreProviderFromAccount(currentSession);
        } catch (_) {
            currentSession = null;
            window.ZyvioAuth.clearSession();
            setAuthStatus("Session expirée. Reconnectez-vous.");
            showAuth();
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
            await registerCurrentDevice();
            const allowed = await refreshSystemState();
            if (!allowed) {
                setAuthStatus("");
                return;
            }
            await restoreProviderFromAccount(currentSession);
            setAuthStatus("");
        } catch (error) {
            currentSession = null;
            setAuthStatus(error?.message || "Connexion impossible.");
            passwordInput?.focus();
        }
    }

    async function signOut() {
        setStatus("Déconnexion…");
        await window.ZyvioAuth.signOut();
        currentSession = null;
        devices = [];
        hideSecondaryPanels();
        showAuth();
        setAuthStatus("Vous êtes déconnecté.");
    }

    function hideCatalog() {
        if (catalogPanel) catalogPanel.hidden = true;
        if (catalogGrid) catalogGrid.replaceChildren();
    }

    function renderCatalog(title, items, kind) {
        if (!catalogPanel || !catalogGrid) return;
        catalogPanel.hidden = false;
        catalogTitle.textContent = title;
        catalogCount.textContent = items.length + " élément" + (items.length > 1 ? "s" : "");
        catalogGrid.replaceChildren();

        items.slice(0, 60).forEach((item) => {
            const button = document.createElement("button");
            button.className = "catalog-card";
            button.dataset.focusable = "";
            button.dataset.catalogKind = kind;
            button.dataset.catalogId = item.id;
            if (item.favoriteType) button.dataset.favoriteType = item.favoriteType;
            if (isFavorite(kind, item.id)) button.classList.add("favorite");

            const strong = document.createElement("strong");
            strong.textContent = item.title || item.name || "Contenu";

            const small = document.createElement("small");
            small.textContent = item.categoryName || "";

            button.append(strong, small);
            catalogGrid.append(button);
        });

        setTimeout(() => catalogGrid.querySelector("[data-focusable]")?.focus(), 0);
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

    function isFavorite(type, id, playlistId = null) {
        const key = favoriteKey(type, id, playlistId);
        return favorites.some((item) => favoriteKey(item) === key);
    }

    function progressFor(type, id, playlistId = null) {
        const key = libraryKey(playlistId || currentPlaylist?.id, type, id);
        return watchProgress.find(
            (item) => libraryKey(item.playlist_id, item.content_type, item.content_id) === key
        ) || null;
    }

    async function toggleFavoriteForFocused() {
        const target = document.activeElement?.closest?.("[data-catalog-kind]");
        if (!currentSession || !currentProfile || !currentPlaylist) return;

        let kind = target?.dataset.catalogKind || "";
        let source = null;

        if (kind === "movie") {
            source = movies.find((item) => item.id === target.dataset.catalogId);
        } else if (kind === "series") {
            source = series.find((item) => item.id === target.dataset.catalogId);
        } else if (kind === "live") {
            source = liveChannels.find((item) => item.id === target.dataset.catalogId);
        } else if (activePlayback?.contentType === "live") {
            kind = "live";
            source = liveChannels.find((item) => item.id === activePlayback.contentId) || {
                id: activePlayback.contentId,
                name: activePlayback.title,
                logo: activePlayback.artworkUrl || null,
            };
        }

        if (!source || !["movie", "series", "live"].includes(kind)) return;

        const title = kind === "live" ? source.name : source.title;
        const artwork = kind === "live" ? source.logo : source.poster;
        const payload = {
            playlistId: currentPlaylist.id,
            contentType: kind,
            contentId: source.id,
            title,
            artworkUrl: artwork || null,
        };

        try {
            if (isFavorite(kind, source.id)) {
                await window.ZyvioCloud.removeFavorite(
                    currentSession,
                    currentProfile.id,
                    payload
                );
                favorites = favorites.filter(
                    (item) => favoriteKey(item) !==
                        favoriteKey(kind, source.id, currentPlaylist.id)
                );
                setStatus(title + " retiré des favoris.");
            } else {
                await window.ZyvioCloud.upsertFavorite(
                    currentSession,
                    currentProfile.id,
                    payload
                );
                favorites.unshift({
                    playlist_id: payload.playlistId,
                    content_type: payload.contentType,
                    content_id: payload.contentId,
                    title: payload.title,
                    artwork_url: payload.artworkUrl,
                });
                setStatus(title + " ajouté aux favoris.");
            }
            if (target) target.classList.toggle("favorite", isFavorite(kind, source.id));
            renderHomeShelves();
        } catch (_) {
            setStatus("Impossible de synchroniser le favori.");
        }
    }

    function activeContentKey(metadata = activePlayback) {
        if (!metadata) return "";
        return contentKey(metadata.contentType || "content", metadata.contentId || "");
    }

    function runtimeExceptionActive() {
        const now = trustedRuntimeNowMs(parentalRuntime);
        return runtimeExceptionUntilMs > 0 && now > 0 && now < runtimeExceptionUntilMs;
    }

    function localRuntimeBlockReason() {
        if (!isChildProfile() || !parentalRuntime?.parentalEnabled || runtimeExceptionActive()) {
            return null;
        }

        if (!scheduleAllowsNow(parentalRuntime)) return "Pas maintenant";

        const limit = effectiveRuntimeLimitMinutes(parentalRuntime);
        const consumed = Number(parentalRuntime.consumedSeconds || 0);
        if (limit != null && consumed >= Number(limit) * 60) {
            return "Temps d’écran atteint";
        }
        return null;
    }

    async function refreshParentalRuntime(contentKeyValue) {
        if (!currentSession || !currentProfile || !isChildProfile()) {
            runtimeBlocked = false;
            return null;
        }

        try {
            const state = await window.ZyvioCloud.getParentalRuntimeState(
                currentSession,
                currentProfile.id,
                contentKeyValue || ""
            );
            saveRuntimeCache(currentProfile.id, state);
            runtimeExceptionUntilMs = Number(state?.exception_until_epoch_ms || 0);
            return state;
        } catch (_) {
            parentalRuntime = loadRuntimeCache(currentProfile.id);
            return null;
        }
    }

    function runtimeExceptionVerifier(contentKeyValue) {
        return async (pin) => {
            try {
                const result = await window.ZyvioCloud.grantParentalException(
                    currentSession,
                    currentProfile.id,
                    pin,
                    contentKeyValue
                );
                if (result?.success) {
                    const expires = Date.parse(result.expires_at || "");
                    runtimeExceptionUntilMs = Number.isFinite(expires)
                        ? expires
                        : trustedRuntimeNowMs(parentalRuntime) + 30 * 60 * 1000;
                    runtimeBlocked = false;
                    return { ok: true };
                }

                const reason = result?.reason;
                const message = reason === "blocked"
                    ? "Trop de tentatives. Réessayez dans quelques minutes."
                    : reason === "pin_not_configured"
                        ? "Aucun code PIN parental n’est configuré."
                        : reason === "pin_invalid"
                            ? "Code PIN incorrect."
                            : "Impossible d’autoriser cette lecture.";
                return { ok: false, message };
            } catch (_) {
                return { ok: false, message: "Impossible de vérifier l’exception parentale." };
            }
        };
    }

    function promptRuntimeException(
        reason,
        contentKeyValue,
        onGranted,
        resumeExistingPlayback = true
    ) {
        if (resumeExistingPlayback) player?.pause();
        runtimeBlocked = true;
        showPinPrompt(
            reason || "Lecture bloquée",
            "Saisissez le PIN parental pour continuer ce contenu pendant 30 minutes.",
            async () => {
                runtimeBlocked = false;
                if (resumeExistingPlayback) player?.resume();
                if (typeof onGranted === "function") await onGranted();
            },
            runtimeExceptionVerifier(contentKeyValue)
        );
    }

    async function canStartPlayback(metadata, onGranted) {
        if (!isChildProfile() || !parentalSettings?.enabled) return true;

        const key = activeContentKey(metadata);
        const state = await refreshParentalRuntime(key);
        if (state?.blocked_by_time && !runtimeExceptionActive()) {
            promptRuntimeException("Temps d’écran atteint", key, onGranted, false);
            return false;
        }

        const reason = localRuntimeBlockReason();
        if (reason) {
            promptRuntimeException(reason, key, onGranted, false);
            return false;
        }
        return true;
    }

    async function finishParentalPlayback() {
        if (!currentSession || !currentProfile || !activePlayback) return;
        const key = activeContentKey(activePlayback);
        try {
            await window.ZyvioCloud.parentalHeartbeat(
                currentSession,
                currentProfile.id,
                deviceUid,
                false,
                key,
                Number(parentalRuntime?.consumedSeconds || 0)
            );
        } catch (_) {}
        if (runtimeExceptionUntilMs > 0) {
            try {
                await window.ZyvioCloud.endParentalException(
                    currentSession,
                    currentProfile.id,
                    key
                );
            } catch (_) {}
        }
        runtimeExceptionUntilMs = 0;
        runtimeBlocked = false;
    }

    async function parentalRuntimeTick() {
        if (!currentProfile || !isChildProfile() || !parentalSettings?.enabled) return;

        parentalRuntime = parentalRuntime || loadRuntimeCache(currentProfile.id);
        const reasonBefore = localRuntimeBlockReason();
        if (reasonBefore && activePlayback && !runtimeBlocked) {
            promptRuntimeException(reasonBefore, activeContentKey(activePlayback));
            return;
        }

        if (!activePlayback) return;

        const key = activeContentKey(activePlayback);
        const playing = !runtimeBlocked && Boolean(player?.isPlaying?.());
        try {
            const heartbeat = await window.ZyvioCloud.parentalHeartbeat(
                currentSession,
                currentProfile.id,
                deviceUid,
                playing,
                key,
                Number(parentalRuntime?.consumedSeconds || 0)
            );

            if (parentalRuntime) {
                const trustedNow = trustedRuntimeNowMs(parentalRuntime);
                const day = utcDayKey(trustedNow);
                if (parentalRuntime.usageDayUtc !== day) {
                    parentalRuntime.usageDayUtc = day;
                    parentalRuntime.consumedSeconds = 0;
                }
                parentalRuntime.consumedSeconds = Math.max(
                    Number(parentalRuntime.consumedSeconds || 0),
                    Number(heartbeat?.consumed_seconds || 0)
                );
                try {
                    localStorage.setItem(
                        runtimeCacheKey(currentProfile.id),
                        JSON.stringify(parentalRuntime)
                    );
                } catch (_) {}
            }

            if (heartbeat?.exception_until) {
                const expires = Date.parse(heartbeat.exception_until);
                if (Number.isFinite(expires)) runtimeExceptionUntilMs = expires;
            }

            if (heartbeat?.blocked_by_time && !runtimeExceptionActive()) {
                promptRuntimeException("Temps d’écran atteint", key);
                return;
            }
        } catch (_) {
            if (playing) updateLocalRuntimeConsumed(30);
        }

        const reasonAfter = localRuntimeBlockReason();
        if (reasonAfter && !runtimeBlocked) {
            promptRuntimeException(reasonAfter, key);
        }
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
            await window.ZyvioCloud.upsertWatchProgress(
                currentSession,
                currentProfile.id,
                payload
            );
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
                completed,
                last_watched_at: new Date().toISOString(),
            });
            renderHomeShelves();
        } catch (_) {}
    }

    async function startTrackedPlayback(url, metadata) {
        await syncActivePlayback();
        await finishParentalPlayback();
        const allowed = await canStartPlayback(
            metadata,
            async () => startTrackedPlayback(url, metadata)
        );
        if (!allowed) return false;
        await player.play(url);
        activePlayback = { ...metadata, trackProgress: true };
        runtimeBlocked = false;
        const previous = progressFor(metadata.contentType, metadata.contentId, currentPlaylist?.id);
        const resumeMs = Number(previous?.position_ms || 0);
        const completed = Boolean(previous?.completed);
        if (!completed && resumeMs >= 10_000) {
            player.seekToMs(resumeMs);
            setStatus("Reprise : " + metadata.title);
        } else {
            setStatus("Lecture : " + metadata.title);
        }
        return true;
    }

    function renderEpisodes(seriesItem, detail) {
        episodes = Object.values(detail.episodesBySeason || {})
            .flat()
            .sort((a, b) => a.season - b.season || a.number - b.number)
            .map((episode) => ({
                ...episode,
                seriesId: seriesItem.id,
                seriesTitle: seriesItem.title,
                poster: seriesItem.poster || null,
                categoryName: "S" + episode.season + " · E" + episode.number,
            }));
        renderCatalog(seriesItem.title, episodes, "episode");
    }

    async function loadFavorites() {
        if (!currentSession || !currentProfile) return;
        try {
            favorites = await window.ZyvioCloud.listFavorites(currentSession, currentProfile.id);
            const visibleFavorites = favorites.filter((item) => {
                if (String(item.playlist_id || "") !== String(currentPlaylist?.id || "")) return false;
                if (!isChildProfile()) return true;
                const key = contentKey(item.content_type, item.content_id);
                if (
                    contentLocks?.parental_enabled &&
                    (contentLocks.locked_content_keys || []).includes(key)
                ) {
                    return false;
                }
                if (item.content_type === "live") {
                    return liveChannels.some(
                        (channel) => channel.id === String(item.content_id)
                    );
                }
                return true;
            });
            const items = visibleFavorites.map((item) => ({
                id: item.content_id,
                title: item.title,
                categoryName: item.content_type === "movie" ? "Film" :
                    item.content_type === "series" ? "Série" : "TV",
                favoriteType: item.content_type,
            }));
            renderCatalog("Favoris", items, "favorite");
            setStatus(items.length ? items.length + " favori(s)." : "Aucun favori.");
        } catch (_) {
            setStatus("Impossible de charger les favoris.");
        }
    }

    function clearHomeContainer(container) {
        if (container) container.replaceChildren();
    }

    function createHomeCard(item, kind, subtitle, progressFraction = null) {
        const button = document.createElement("button");
        button.className = "card landscape home-item";
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

    function setShelfMore(kind, total) {
        const button = document.querySelector('[data-home-see-all="' + kind + '"]');
        if (button) button.hidden = Number(total || 0) <= 20;
    }

    function recentByAdded(items) {
        return [...(items || [])]
            .sort((a, b) => Number(b.added || 0) - Number(a.added || 0));
    }

    function lastViewedCatalogItem() {
        const activePlaylistId = String(currentPlaylist?.id || "");
        for (const progress of watchProgress) {
            if (String(progress.playlist_id || "") !== activePlaylistId) continue;
            if (progress.content_type === "movie") {
                const item = movies.find((movie) => String(movie.id) === String(progress.content_id));
                if (item) return { type: "movie", item };
            }
            if (progress.content_type === "episode" && progress.series_id) {
                const item = series.find((entry) => String(entry.id) === String(progress.series_id));
                if (item) return { type: "series", item };
            }
        }
        return null;
    }

    function renderStaticHomeCatalogShelves() {
        const recentMovies = recentByAdded(filterForProfile("movie", movies));
        clearHomeContainer(recentMovieCards);
        recentMovies.slice(0, 20).forEach((item) => {
            recentMovieCards?.append(createHomeCard(
                { id: item.id, title: item.title, content_type: "movie", playlist_id: currentPlaylist?.id },
                "recent-movie",
                item.categoryName || "Film"
            ));
        });
        if (recentMoviesShelf) recentMoviesShelf.hidden = recentMovies.length === 0;
        setShelfMore("recent-movies", recentMovies.length);

        const recentSeriesItems = recentByAdded(filterForProfile("series", series));
        clearHomeContainer(recentSeriesCards);
        recentSeriesItems.slice(0, 20).forEach((item) => {
            recentSeriesCards?.append(createHomeCard(
                { id: item.id, title: item.title, content_type: "series", playlist_id: currentPlaylist?.id },
                "recent-series",
                item.categoryName || "Série"
            ));
        });
        if (recentSeriesShelf) recentSeriesShelf.hidden = recentSeriesItems.length === 0;
        setShelfMore("recent-series", recentSeriesItems.length);

        const last = lastViewedCatalogItem();
        const related = last
            ? (last.type === "movie" ? movies : series).filter((item) =>
                item.categoryId &&
                item.categoryId === last.item.categoryId &&
                String(item.id) !== String(last.item.id) &&
                !isLockedItem(last.type, item)
            )
            : [];
        clearHomeContainer(sameCategoryCards);
        related.slice(0, 20).forEach((item) => {
            sameCategoryCards?.append(createHomeCard(
                { id: item.id, title: item.title, content_type: last.type, playlist_id: currentPlaylist?.id },
                "same-category",
                item.categoryName || ""
            ));
        });
        if (sameCategoryTitle && last) {
            sameCategoryTitle.textContent = last.item.categoryName
                ? "Parce que vous avez regardé · " + last.item.categoryName
                : "Dans la même catégorie";
        }
        if (sameCategoryShelf) sameCategoryShelf.hidden = related.length === 0;
        setShelfMore("same-category", related.length);
    }

    async function buildNextEpisodes() {
        if (!providerConfig || providerConfig.type !== "xtream") {
            nextEpisodes = [];
            return;
        }

        const activePlaylistId = String(currentPlaylist?.id || "");
        const latestBySeries = new Map();
        watchProgress
            .filter((item) =>
                String(item.playlist_id || "") === activePlaylistId &&
                item.content_type === "episode" &&
                item.series_id
            )
            .forEach((item) => {
                const key = String(item.series_id);
                if (!latestBySeries.has(key)) latestBySeries.set(key, item);
            });

        const candidates = [];
        for (const [seriesId, progress] of Array.from(latestBySeries.entries()).slice(0, 12)) {
            try {
                const detail = await window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, seriesId);
                const ordered = Object.values(detail.episodesBySeason || {})
                    .flat()
                    .sort((a, b) => a.season - b.season || a.number - b.number);
                const index = ordered.findIndex((item) => String(item.id) === String(progress.content_id));
                const next = index >= 0 ? ordered[index + 1] : null;
                if (!next) continue;
                const seriesItem = series.find((item) => String(item.id) === seriesId);
                if (seriesItem && isLockedItem("series", seriesItem)) continue;
                candidates.push({
                    ...next,
                    seriesId,
                    seriesTitle: seriesItem?.title || progress.title || "Série",
                    poster: seriesItem?.poster || progress.artwork_url || null,
                    categoryName: seriesItem?.categoryName || "",
                });
            } catch (_) {}
        }
        nextEpisodes = candidates;
    }

    function renderNextEpisodes() {
        clearHomeContainer(nextEpisodeCards);
        nextEpisodes.slice(0, 20).forEach((episode) => {
            nextEpisodeCards?.append(createHomeCard(
                {
                    id: episode.id,
                    title: episode.seriesTitle,
                    content_type: "episode",
                    playlist_id: currentPlaylist?.id,
                },
                "next-episode",
                "S" + episode.season + " · E" + episode.number
            ));
        });
        if (nextEpisodesShelf) nextEpisodesShelf.hidden = nextEpisodes.length === 0;
        setShelfMore("next-episodes", nextEpisodes.length);
    }

    async function refreshHomeCatalogShelves() {
        if (!providerConfig || providerConfig.type !== "xtream") {
            nextEpisodes = [];
            if (nextEpisodesShelf) nextEpisodesShelf.hidden = true;
            if (recentMoviesShelf) recentMoviesShelf.hidden = true;
            if (recentSeriesShelf) recentSeriesShelf.hidden = true;
            if (sameCategoryShelf) sameCategoryShelf.hidden = true;
            return;
        }

        const token = ++homeRefreshToken;
        try {
            if (!movies.length || !series.length) {
                const [loadedMovies, loadedSeries] = await Promise.all([
                    movies.length ? Promise.resolve(movies) : window.ZyvioProvider.loadMovies(providerConfig),
                    series.length ? Promise.resolve(series) : window.ZyvioProvider.loadSeries(providerConfig),
                ]);
                if (token !== homeRefreshToken) return;
                movies = filterForProfile("movie", loadedMovies);
                series = filterForProfile("series", loadedSeries);
            }

            renderStaticHomeCatalogShelves();
            await buildNextEpisodes();
            if (token !== homeRefreshToken) return;
            renderNextEpisodes();
        } catch (_) {
            if (token !== homeRefreshToken) return;
            setStatus("Certaines recommandations sont momentanément indisponibles.");
        }
    }

    function renderHomeShelves() {
        const activePlaylistId = String(currentPlaylist?.id || "");

        const allResumable = watchProgress.filter((item) =>
            String(item.playlist_id || "") === activePlaylistId &&
            !item.completed &&
            Number(item.position_ms || 0) >= 10_000
        );
        const resumable = allResumable.slice(0, 20);
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
        setShelfMore("continue", allResumable.length);

        const allRecentChannels = liveHistory.filter((item) => {
            if (String(item.playlist_id || "") !== activePlaylistId) return false;
            if (!isChildProfile()) return true;
            return liveChannels.some((channel) => String(channel.id) === String(item.channel_id));
        });
        const recentChannels = allRecentChannels.slice(0, 20);
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
        setShelfMore("recent-live", allRecentChannels.length);

        const allFavoriteItems = favorites.filter((item) => {
            if (String(item.playlist_id || "") !== activePlaylistId) return false;
            if (isChildProfile() && item.content_type === "live") {
                return liveChannels.some((channel) => String(channel.id) === String(item.content_id));
            }
            return true;
        });
        const favoriteItems = allFavoriteItems.slice(0, 20);
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
        setShelfMore("favorites", allFavoriteItems.length);

        refreshHomeCatalogShelves();
    }

    async function resolveProgressPlayback(progress) {
        if (String(progress?.playlist_id || "") !== String(currentPlaylist?.id || "")) {
            throw new Error("Ce contenu appartient à une autre playlist.");
        }

        if (progress.content_type === "movie") {
            if (!movies.length) movies = await window.ZyvioProvider.loadMovies(providerConfig);
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
            const detail = await window.ZyvioProvider.loadXtreamSeriesInfo(
                providerConfig,
                String(progress.series_id)
            );
            const all = Object.values(detail.episodesBySeason || {}).flat();
            const episode = all.find((item) => item.id === String(progress.content_id));
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

    async function loadMovies() {
        if (!providerConfig) {
            setStatus("Aucun fournisseur configuré.");
            return;
        }
        setStatus("Chargement des films…");
        try {
            movies = filterForProfile(
                "movie",
                await window.ZyvioProvider.loadMovies(providerConfig)
            );
            renderCatalog("Films", movies, "movie");
            setStatus(movies.length ? movies.length + " films chargés." : "Aucun film disponible.");
        } catch (error) {
            hideCatalog();
            setStatus(error?.message || "Impossible de charger les films.");
        }
    }

    async function loadSeries() {
        if (!providerConfig) {
            setStatus("Aucun fournisseur configuré.");
            return;
        }
        setStatus("Chargement des séries…");
        try {
            series = filterForProfile(
                "series",
                await window.ZyvioProvider.loadSeries(providerConfig)
            );
            renderCatalog("Séries", series, "series");
            setStatus(series.length ? series.length + " séries chargées." : "Aucune série disponible.");
        } catch (error) {
            hideCatalog();
            setStatus(error?.message || "Impossible de charger les séries.");
        }
    }

    async function loadProviderLive() {
        if (!providerConfig) {
            setStatus("Aucun fournisseur configuré.");
            return [];
        }
        setStatus("Chargement des chaînes…");
        try {
            liveChannels = filterForProfile(
                "live",
                await window.ZyvioProvider.loadLive(providerConfig)
            );
            renderHomeShelves();
            setStatus(liveChannels.length + " chaînes chargées.");
            return liveChannels;
        } catch (error) {
            setStatus(error?.message || "Impossible de charger les chaînes.");
            return [];
        }
    }

    async function playChannel(channelOrIndex = 0) {
        if (!liveChannels.length) await loadProviderLive();
        const channel = typeof channelOrIndex === "number"
            ? liveChannels[channelOrIndex]
            : channelOrIndex;
        if (!channel) {
            setStatus("Chaîne introuvable.");
            return;
        }
        try {
            await syncActivePlayback();
            await finishParentalPlayback();
            const metadata = {
                contentType: "live",
                contentId: channel.id,
                title: channel.name,
                artworkUrl: channel.logo || null,
                trackProgress: false,
            };
            const allowed = await canStartPlayback(
                metadata,
                async () => playChannel(channel)
            );
            if (!allowed) return;
            await player.play(channel.streamUrl);
            activePlayback = metadata;
            runtimeBlocked = false;
            try {
                await window.ZyvioCloud.recordLiveHistory(
                    currentSession,
                    currentProfile.id,
                    {
                        playlistId: currentPlaylist.id,
                        channelId: channel.id,
                        channelName: channel.name,
                        logoUrl: channel.logo || null,
                    }
                );
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
                    const epg = await window.ZyvioProvider.loadXtreamShortEpg(
                        providerConfig,
                        channel.id,
                        6
                    );
                    const now = Math.floor(Date.now() / 1000);
                    const current = epg.find((item) => item.start <= now && item.end > now);
                    if (current?.title) epgSuffix = " — " + current.title;
                } catch (_) {}
            }
            setStatus("Lecture : " + channel.name + epgSuffix);
        } catch (_) {
            setStatus("Flux indisponible.");
        }
    }

    window.ZyvioTV = Object.freeze({
        setProviderConfig(config) {
            providerConfig = config ? { ...config } : null;
            liveChannels = [];
            return Boolean(providerConfig);
        },
        clearProviderConfig() {
            providerConfig = null;
            liveChannels = [];
            player?.stop();
        },
        loadLive: loadProviderLive,
        playChannel,
        stopPlayback() {
            player?.stop();
        },
        getLiveChannels() {
            return liveChannels.map(({ streamUrl, ...safe }) => ({ ...safe }));
        },
    });


    function focusables() {
        return Array.from(document.querySelectorAll(focusableSelector))
            .filter((element) => !element.disabled && element.offsetParent !== null);
    }

    function setStatus(message) {
        if (status) status.textContent = message;
    }

    function nearestCandidate(current, direction) {
        const currentRect = current.getBoundingClientRect();
        const currentCenter = {
            x: currentRect.left + currentRect.width / 2,
            y: currentRect.top + currentRect.height / 2,
        };

        return focusables()
            .filter((candidate) => candidate !== current)
            .map((candidate) => {
                const rect = candidate.getBoundingClientRect();
                const center = {
                    x: rect.left + rect.width / 2,
                    y: rect.top + rect.height / 2,
                };
                const dx = center.x - currentCenter.x;
                const dy = center.y - currentCenter.y;

                const valid =
                    (direction === "left" && dx < -8) ||
                    (direction === "right" && dx > 8) ||
                    (direction === "up" && dy < -8) ||
                    (direction === "down" && dy > 8);

                if (!valid) return null;

                const primary = direction === "left" || direction === "right"
                    ? Math.abs(dx)
                    : Math.abs(dy);
                const secondary = direction === "left" || direction === "right"
                    ? Math.abs(dy)
                    : Math.abs(dx);

                return {
                    candidate,
                    score: primary + secondary * 2.25,
                };
            })
            .filter(Boolean)
            .sort((a, b) => a.score - b.score)[0]?.candidate || null;
    }

    function moveFocus(direction) {
        const current = document.activeElement;
        const items = focusables();

        if (!items.length) return;
        if (!current || !current.matches(focusableSelector)) {
            items[0].focus();
            return;
        }

        const candidate = nearestCandidate(current, direction);
        if (candidate) {
            candidate.focus();
            candidate.scrollIntoView({ block: "nearest", inline: "nearest" });
        }
    }

    function activateSection(section) {
        if (section !== "more") hideSecondaryPanels();
        document.querySelectorAll(".nav-item").forEach((item) => {
            item.classList.toggle("active", item.dataset.section === section);
        });

        const labels = {
            home: "Accueil",
            live: "TV en direct",
            movies: "Films",
            series: "Séries",
            favorites: "Favoris",
            profiles: "Profils",
            more: "Plus",
        };

        setStatus(labels[section] || section);
    }

    function exitApplication() {
        try {
            window.tizen?.application?.getCurrentApplication?.().exit();
            return;
        } catch (_) {}
        setStatus("Utilisez Retour pour quitter ZYVIOTV.");
    }

    function focusHomeNavigation() {
        const home = document.querySelector('[data-section="home"]');
        setTimeout(() => home?.focus(), 0);
    }

    async function stopCurrentPlayback(statusMessage = "Retour") {
        if (!player?.isActive?.() && !activePlayback) return false;
        try { await syncActivePlayback(); } catch (_) {}
        try { await finishParentalPlayback(); } catch (_) {}
        activePlayback = null;
        player?.stop();
        setStatus(statusMessage);
        focusHomeNavigation();
        return true;
    }

    function returnFromSecondaryPanel() {
        if (devicesPanel && !devicesPanel.hidden) {
            devicesPanel.hidden = true;
            if (morePanel) morePanel.hidden = false;
            setStatus("Plus");
            setTimeout(() => morePanel?.querySelector("[data-focusable]")?.focus(), 0);
            return true;
        }

        if (morePanel && !morePanel.hidden) {
            morePanel.hidden = true;
            activateSection("home");
            renderHomeShelves();
            focusHomeNavigation();
            return true;
        }

        if (catalogPanel && !catalogPanel.hidden) {
            hideCatalog();
            activateSection("home");
            renderHomeShelves();
            focusHomeNavigation();
            return true;
        }

        return false;
    }

    function registerRemoteKeys() {
        if (!window.tizen?.tvinputdevice) return;

        [
            "MediaPlayPause",
            "MediaPlay",
            "MediaPause",
            "MediaStop",
            "MediaFastForward",
            "MediaRewind",
            "ColorF0Red",
        ].forEach((key) => {
            try {
                window.tizen.tvinputdevice.registerKey(key);
            } catch (_) {
                // Optional keys differ between Samsung models.
            }
        });
    }

    document.addEventListener("keydown", (event) => {
        const keyMap = {
            ArrowLeft: "left",
            ArrowRight: "right",
            ArrowUp: "up",
            ArrowDown: "down",
        };

        const direction = keyMap[event.key];
        if (direction) {
            event.preventDefault();
            moveFocus(direction);
            return;
        }

        const mediaKey = event.key || "";
        if (mediaKey === "MediaPlayPause") {
            event.preventDefault();
            if (player?.togglePause?.()) {
                setStatus(player.paused ? "Pause" : "Lecture");
            }
            return;
        }
        if (mediaKey === "MediaPlay") {
            event.preventDefault();
            player?.resume();
            return;
        }
        if (mediaKey === "MediaPause") {
            event.preventDefault();
            player?.pause();
            return;
        }
        if (mediaKey === "MediaStop") {
            event.preventDefault();
            stopCurrentPlayback("Lecture arrêtée.");
            return;
        }
        if (mediaKey === "MediaFastForward") {
            event.preventDefault();
            if (activePlayback?.contentType !== "live") player?.seekByMs?.(30_000);
            return;
        }
        if (mediaKey === "MediaRewind") {
            event.preventDefault();
            if (activePlayback?.contentType !== "live") player?.seekByMs?.(-30_000);
            return;
        }

        if (event.key === "ColorF0Red" || event.keyCode === 403) {
            event.preventDefault();
            if (document.activeElement?.closest?.("[data-device-id]")) {
                disconnectFocusedDevice();
            } else {
                toggleFavoriteForFocused();
            }
            return;
        }

        if (pinScreen && !pinScreen.hidden && /^\d$/.test(event.key) && document.activeElement === pinInput) {
            pinInput.value = (pinInput.value + event.key).replace(/\D/g, "").slice(0, 4);
            event.preventDefault();
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

        const isBack =
            event.key === "Backspace" ||
            event.key === "Escape" ||
            event.key === "XF86Back" ||
            event.keyCode === 10009;

        if (isBack) {
            event.preventDefault();

            if (deviceEditScreen && !deviceEditScreen.hidden) {
                closeDeviceRename();
                return;
            }

            if (pinScreen && !pinScreen.hidden) {
                closePinPrompt();
                return;
            }

            if (systemScreen && !systemScreen.hidden) {
                if (systemState.type === "maintenance_planned") {
                    plannedMaintenanceDismissed = true;
                    systemScreen.hidden = true;
                    if (!currentProfile) restoreProviderFromAccount(currentSession);
                } else {
                    exitApplication();
                }
                return;
            }

            if (profileScreen && !profileScreen.hidden) {
                if (currentProfile) {
                    profileScreen.hidden = true;
                    showApp();
                    focusHomeNavigation();
                } else {
                    exitApplication();
                }
                return;
            }

            if (player?.isActive?.() || activePlayback) {
                stopCurrentPlayback();
                return;
            }

            if (returnFromSecondaryPanel()) return;

            exitApplication();
        }
    });

    document.addEventListener("click", async (event) => {
        const target = event.target.closest(
            "[data-section], [data-action], [data-catalog-kind], [data-profile-id], [data-home-kind], [data-device-id]"
        );
        if (!target) return;

        if (target.dataset.deviceId) {
            const device = devices.find((item) => item.id === target.dataset.deviceId);
            if (device) openDeviceRename(device);
            return;
        }

        if (target.dataset.profileId) {
            selectProfile(target.dataset.profileId).catch(() => {
                setProfileStatus("Impossible de charger ce profil.");
            });
            return;
        }

        if (target.dataset.homeKind === "continue" || target.dataset.homeKind === "history") {
            const targetKey = libraryKey(
                target.dataset.homePlaylist,
                target.dataset.homeType,
                target.dataset.homeId
            );
            const item = watchProgress.find(
                (entry) => libraryKey(entry.playlist_id, entry.content_type, entry.content_id) === targetKey
            );
            if (item) {
                resolveProgressPlayback(item).catch(() => setStatus("Contenu indisponible."));
            }
            return;
        }

        if (target.dataset.homeKind === "recent-live") {
            if (String(target.dataset.homePlaylist || "") !== String(currentPlaylist?.id || "")) {
                setStatus("Cette chaîne appartient à une autre playlist.");
                return;
            }
            if (!liveChannels.length) await loadProviderLive();
            const channel = liveChannels.find(
                (entry) => String(entry.id) === String(target.dataset.homeId)
            );
            if (channel) {
                playChannel(channel);
            } else {
                setStatus("Chaîne indisponible.");
            }
            return;
        }

        if (target.dataset.homeKind === "favorite") {
            const targetKey = libraryKey(
                target.dataset.homePlaylist,
                target.dataset.homeType,
                target.dataset.homeId
            );
            const item = favorites.find(
                (entry) => libraryKey(entry.playlist_id, entry.content_type, entry.content_id) === targetKey
            );
            if (item) {
                if (item.content_type === "movie") {
                    if (!movies.length) movies = await window.ZyvioProvider.loadMovies(providerConfig);
                    const movie = movies.find((entry) => entry.id === String(item.content_id));
                    if (movie) {
                        startTrackedPlayback(movie.streamUrl, {
                            contentType: "movie",
                            contentId: movie.id,
                            title: movie.title,
                            artworkUrl: movie.poster || null,
                        }).catch(() => setStatus("Film indisponible."));
                    }
                } else if (item.content_type === "series") {
                    if (!series.length) series = await window.ZyvioProvider.loadSeries(providerConfig);
                    const seriesItem = series.find((entry) => entry.id === String(item.content_id));
                    if (seriesItem) {
                        window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, seriesItem.id)
                            .then((info) => renderEpisodes(seriesItem, info))
                            .catch(() => setStatus("Détails de série indisponibles."));
                    }
                } else if (item.content_type === "live") {
                    if (!liveChannels.length) await loadProviderLive();
                    const channel = liveChannels.find(
                        (entry) => entry.id === String(item.content_id)
                    );
                    if (channel) playChannel(channel);
                    else setStatus("Chaîne indisponible.");
                }
            }
            return;
        }

        if (target.dataset.action === "verify-pin") {
            verifyPendingPin();
            return;
        }

        if (target.dataset.action === "cancel-pin") {
            closePinPrompt();
            return;
        }

        if (target.dataset.action === "system-continue") {
            plannedMaintenanceDismissed = true;
            if (systemScreen) systemScreen.hidden = true;
            if (!currentProfile) await restoreProviderFromAccount(currentSession);
            return;
        }

        if (target.dataset.action === "open-devices") {
            await loadDevices();
            return;
        }

        if (target.dataset.action === "save-device-name") {
            await saveDeviceRename();
            return;
        }

        if (target.dataset.action === "cancel-device-name") {
            closeDeviceRename();
            return;
        }

        if (target.dataset.action === "sign-in") {
            signIn();
            return;
        }

        if (target.dataset.action === "open-live") {
            hideCatalog();
            activateSection("live");
            loadProviderLive();
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
                signOut();
            }
            return;
        }

        if (target.dataset.catalogKind === "movie") {
            const movie = movies.find((item) => item.id === target.dataset.catalogId);
            if (movie) {
                startTrackedPlayback(movie.streamUrl, {
                    contentType: "movie",
                    contentId: movie.id,
                    title: movie.title,
                    artworkUrl: movie.poster || null,
                }).catch(() => setStatus("Film indisponible."));
            }
            return;
        }

        if (target.dataset.catalogKind === "series") {
            const item = series.find((entry) => entry.id === target.dataset.catalogId);
            if (!item) return;
            setStatus("Chargement : " + item.title + "…");
            window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, item.id)
                .then((info) => {
                    renderEpisodes(item, info);
                    const count = Object.values(info.episodesBySeason || {})
                        .reduce((sum, value) => sum + (Array.isArray(value) ? value.length : 0), 0);
                    setStatus(item.title + " — " + count + " épisode(s).");
                })
                .catch(() => setStatus("Détails de série indisponibles."));
            return;
        }

        if (target.dataset.catalogKind === "episode") {
            const episode = episodes.find((item) => item.id === target.dataset.catalogId);
            if (!episode) return;
            startTrackedPlayback(episode.streamUrl, {
                contentType: "episode",
                contentId: episode.id,
                title: episode.seriesTitle + " — S" + episode.season + "E" + episode.number,
                seriesId: episode.seriesId,
                seasonNumber: episode.season,
                episodeNumber: episode.number,
                artworkUrl: episode.poster || null,
            }).catch(() => setStatus("Épisode indisponible."));
            return;
        }

        if (target.dataset.catalogKind === "favorite") {
            const type = target.dataset.favoriteType;
            const id = target.dataset.catalogId;
            if (type === "movie") {
                if (!movies.length) await loadMovies();
                const movie = movies.find((item) => item.id === id);
                if (movie) {
                    startTrackedPlayback(movie.streamUrl, {
                        contentType: "movie",
                        contentId: movie.id,
                        title: movie.title,
                        artworkUrl: movie.poster || null,
                    }).catch(() => setStatus("Film indisponible."));
                }
            } else if (type === "series") {
                if (!series.length) await loadSeries();
                const item = series.find((entry) => entry.id === id);
                if (item) {
                    window.ZyvioProvider.loadXtreamSeriesInfo(providerConfig, item.id)
                        .then((info) => renderEpisodes(item, info))
                        .catch(() => setStatus("Détails de série indisponibles."));
                }
            } else if (type === "live") {
                if (!liveChannels.length) await loadProviderLive();
                const channel = liveChannels.find((item) => item.id === id);
                if (channel) playChannel(channel);
                else setStatus("Chaîne indisponible.");
            }
            return;
        }

        if (target.dataset.section === "home") {
            hideCatalog();
            activateSection("home");
            renderHomeShelves();
            return;
        }

        if (target.dataset.section === "profiles") {
            if (isChildProfile()) {
                showPinPrompt(
                    "Quitter le profil Enfant",
                    "Le code PIN parental est requis pour changer de profil.",
                    async () => showProfilePicker()
                );
            } else {
                showProfilePicker();
            }
            return;
        }

        if (target.dataset.section === "more") {
            const openMore = async () => {
                hideCatalog();
                activateSection("more");
                if (devicesPanel) devicesPanel.hidden = true;
                if (morePanel) morePanel.hidden = false;
                setStatus("Plus");
                setTimeout(() => morePanel?.querySelector("[data-focusable]")?.focus(), 0);
            };
            if (isChildProfile()) {
                showPinPrompt(
                    "Zone protégée",
                    "Le code PIN parental est requis pour ouvrir les réglages du compte.",
                    openMore
                );
            } else {
                await openMore();
            }
            return;
        }

        if (target.dataset.section === "favorites") {
            activateSection("favorites");
            loadFavorites();
            return;
        }

        if (target.dataset.section === "movies") {
            activateSection("movies");
            loadMovies();
            return;
        }

        if (target.dataset.section === "series") {
            activateSection("series");
            loadSeries();
            return;
        }

        if (target.dataset.section === "live") {
            hideCatalog();
            activateSection("live");
            loadProviderLive();
            return;
        }

        if (target.dataset.section) {
            hideCatalog();
            activateSection(target.dataset.section);
        }
    });

    window.addEventListener("load", () => {
        registerRemoteKeys();
        restoreAccount();
        setInterval(() => {
            syncActivePlayback();
            parentalRuntimeTick();
        }, 30_000);
    });
})();
