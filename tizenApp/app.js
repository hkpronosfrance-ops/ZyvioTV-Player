(() => {
    "use strict";

    const focusableSelector = "[data-focusable]";
    const status = document.getElementById("status");

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
            setStatus("Retour");
        }
    });

    document.addEventListener("click", (event) => {
        const target = event.target.closest("[data-section], [data-action]");
        if (!target) return;

        if (target.dataset.action === "open-live") {
            activateSection("live");
            return;
        }

        if (target.dataset.section) {
            activateSection(target.dataset.section);
        }
    });

    window.addEventListener("load", () => {
        registerRemoteKeys();
        focusables()[0]?.focus();
        setStatus("Prêt — navigation télécommande activée.");
    });
})();
