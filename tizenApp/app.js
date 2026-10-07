(() => {
    "use strict";

    const focusableSelector = "[data-focusable]";
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
    const favoritesShelf = document.getElementById("favorites-shelf");
    const favoriteCards = document.getElementById("favorite-cards");
    const historyShelf = document.getElementById("history-shelf");
    const historyCards = document.getElementById("history-cards");
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
    let parentalSettings = null;
    let contentLocks = null;
    let pendingPinAction = null;
    let activePlayback = null;

    const PROFILE_STORAGE_KEY = "zyviotv.tizen.profile.v1";

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
        if (!isChildProfile() || !contentLocks?.parental_enabled) return false;

        const lockedContent = new Set(contentLocks.locked_content_keys || []);
        const lockedCategories = new Set(contentLocks.locked_category_keys || []);
        const itemContentKey = contentKey(kind, item.id);
        const itemCategoryKey = categoryKey(kind, item.categoryId || "");

        if (lockedContent.has(itemContentKey)) return true;
        if (item.categoryId && lockedCategories.has(itemCategoryKey)) return true;
        if (isAdultCategoryLabel(item.categoryName || "")) return true;
        return false;
    }

    function filterForProfile(kind, items) {
        if (!isChildProfile() || !contentLocks?.parental_enabled) return items;
        return items.filter((item) => !isLockedItem(kind, item));
    }

    function showPinPrompt(title, copy, action) {
        pendingPinAction = action;
        if (pinTitle) pinTitle.textContent = title || "Code PIN requis";
        if (pinCopy) pinCopy.textContent = copy || "Saisissez le code PIN parental à 4 chiffres.";
        if (pinInput) pinInput.value = "";
        setPinStatus("");
        if (pinScreen) pinScreen.hidden = false;
        setTimeout(() => pinInput?.focus(), 0);
    }

    function closePinPrompt() {
        pendingPinAction = null;
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

        [favorites, watchProgress, parentalSettings, contentLocks] = await Promise.all([
            window.ZyvioCloud.listFavorites(currentSession, profile.id),
            window.ZyvioCloud.listWatchProgress(currentSession, profile.id, 100),
            window.ZyvioCloud.getParentalSettings(currentSession),
            window.ZyvioCloud.getProfileContentLocks(currentSession, profile.id),
        ]);

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


    function favoriteKey(type, id) {
        return type + ":" + String(id);
    }

    function isFavorite(type, id) {
        const key = favoriteKey(type, id);
        return favorites.some((item) => favoriteKey(item.content_type, item.content_id) === key);
    }

    function progressFor(type, id) {
        return watchProgress.find(
            (item) => item.content_type === type && String(item.content_id) === String(id)
        ) || null;
    }

    async function toggleFavoriteForFocused() {
        const target = document.activeElement?.closest?.("[data-catalog-kind]");
        if (!target || !currentSession || !currentProfile || !currentPlaylist) return;

        const kind = target.dataset.catalogKind;
        if (kind !== "movie" && kind !== "series") return;

        const source = kind === "movie"
            ? movies.find((item) => item.id === target.dataset.catalogId)
            : series.find((item) => item.id === target.dataset.catalogId);
        if (!source) return;

        const payload = {
            playlistId: currentPlaylist.id,
            contentType: kind,
            contentId: source.id,
            title: source.title,
            artworkUrl: source.poster || null,
        };

        try {
            if (isFavorite(kind, source.id)) {
                await window.ZyvioCloud.removeFavorite(
                    currentSession,
                    currentProfile.id,
                    payload
                );
                favorites = favorites.filter(
                    (item) => favoriteKey(item.content_type, item.content_id) !==
                        favoriteKey(kind, source.id)
                );
                setStatus(source.title + " retiré des favoris.");
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
                setStatus(source.title + " ajouté aux favoris.");
            }
            target.classList.toggle("favorite", isFavorite(kind, source.id));
        } catch (_) {
            setStatus("Impossible de synchroniser le favori.");
        }
    }

    async function syncActivePlayback() {
        if (!activePlayback || !currentSession || !currentProfile || !currentPlaylist) return;

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
                (item) => !(
                    item.content_type === payload.contentType &&
                    String(item.content_id) === String(payload.contentId)
                )
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
        await player.play(url);
        activePlayback = metadata;
        const previous = progressFor(metadata.contentType, metadata.contentId);
        const resumeMs = Number(previous?.position_ms || 0);
        const completed = Boolean(previous?.completed);
        if (!completed && resumeMs >= 10_000) {
            player.seekToMs(resumeMs);
            setStatus("Reprise : " + metadata.title);
        } else {
            setStatus("Lecture : " + metadata.title);
        }
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
                if (!isChildProfile() || !contentLocks?.parental_enabled) return true;
                const key = contentKey(item.content_type, item.content_id);
                return !(contentLocks.locked_content_keys || []).includes(key);
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
            .filter((item) => !item.completed && Number(item.position_ms || 0) >= 10_000)
            .slice(0, 20);
        clearHomeContainer(continueCards);
        resumable.forEach((item) => {
            const duration = Number(item.duration_ms || 0);
            const fraction = duration > 0 ? Number(item.position_ms || 0) / duration : 0;
            const label = item.content_type === "episode" && item.season_number != null
                ? "S" + item.season_number + " · E" + item.episode_number
                : "Film";
            continueCards.append(createHomeCard(item, "continue", label, fraction));
        });
        if (continueShelf) continueShelf.hidden = resumable.length === 0;

        const favoriteItems = favorites.slice(0, 20);
        clearHomeContainer(favoriteCards);
        favoriteItems.forEach((item) => {
            const label = item.content_type === "movie" ? "Film" :
                item.content_type === "series" ? "Série" : "TV";
            favoriteCards.append(createHomeCard(item, "favorite", label));
        });
        if (favoritesShelf) favoritesShelf.hidden = favoriteItems.length === 0;

        const recent = watchProgress.slice(0, 20);
        clearHomeContainer(historyCards);
        recent.forEach((item) => {
            const label = item.content_type === "episode" && item.season_number != null
                ? "S" + item.season_number + " · E" + item.episode_number
                : "Film";
            historyCards.append(createHomeCard(item, "history", label));
        });
        if (historyShelf) historyShelf.hidden = recent.length === 0;
    }

    async function resolveProgressPlayback(progress) {
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
            await player.play(channel.streamUrl);
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

        setStatus((labels[section] || section) + " — intégration des données réelles à connecter.");
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

        if (event.key === "ColorF0Red" || event.keyCode === 403) {
            event.preventDefault();
            toggleFavoriteForFocused();
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

        if (event.key === "Backspace" || event.key === "Escape") {
            event.preventDefault();
            syncActivePlayback();
            activePlayback = null;
            player?.stop();
            setStatus("Retour");
        }
    });

    document.addEventListener("click", async (event) => {
        const target = event.target.closest(
            "[data-section], [data-action], [data-catalog-kind], [data-profile-id], [data-home-kind]"
        );
        if (!target) return;

        if (target.dataset.profileId) {
            selectProfile(target.dataset.profileId).catch(() => {
                setProfileStatus("Impossible de charger ce profil.");
            });
            return;
        }

        if (target.dataset.homeKind === "continue" || target.dataset.homeKind === "history") {
            const item = watchProgress.find(
                (entry) => String(entry.content_id) === String(target.dataset.homeId)
            );
            if (item) {
                resolveProgressPlayback(item).catch(() => setStatus("Contenu indisponible."));
            }
            return;
        }

        if (target.dataset.homeKind === "favorite") {
            const item = favorites.find(
                (entry) => String(entry.content_id) === String(target.dataset.homeId)
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
            if (isChildProfile() && parentalSettings?.enabled) {
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
            if (isChildProfile() && parentalSettings?.enabled) {
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
            if (isChildProfile() && parentalSettings?.enabled) {
                showPinPrompt(
                    "Zone protégée",
                    "Le code PIN parental est requis pour ouvrir les réglages du compte.",
                    async () => {
                        hideCatalog();
                        activateSection("more");
                        setStatus("Plus — appuyez de nouveau pour vous déconnecter.");
                        target.dataset.action = "sign-out";
                    }
                );
            } else {
                hideCatalog();
                activateSection("more");
                setStatus("Plus — appuyez de nouveau pour vous déconnecter.");
                target.dataset.action = "sign-out";
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
        setInterval(() => { syncActivePlayback(); }, 30_000);
    });
})();
