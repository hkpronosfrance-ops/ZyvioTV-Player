(() => {
  "use strict";

  const selector = "[data-focusable]";
  const status = document.getElementById("status");

  function items() {
    return Array.from(document.querySelectorAll(selector)).filter((el) => !el.disabled && el.offsetParent !== null);
  }

  function setStatus(text) {
    if (status) status.textContent = text;
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

  function activate(section) {
    document.querySelectorAll(".nav").forEach((item) => {
      item.classList.toggle("active", item.dataset.section === section);
    });

    const label = {
      home: "Accueil",
      live: "TV en direct",
      movies: "Films",
      series: "Séries",
      favorites: "Favoris",
      more: "Plus"
    }[section] || section;

    setStatus(label + " — données réelles à connecter.");
  }

  document.addEventListener("keydown", (event) => {
    const directions = {
      ArrowLeft: "left",
      ArrowRight: "right",
      ArrowUp: "up",
      ArrowDown: "down"
    };

    if (directions[event.key]) {
      event.preventDefault();
      move(directions[event.key]);
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
      setStatus("Retour");
    }
  });

  document.addEventListener("click", (event) => {
    const target = event.target.closest("[data-section]");
    if (target?.dataset.section) {
      activate(target.dataset.section);
      if (target.dataset.section === "live") loadProviderLive();
    }
  });

  window.addEventListener("load", () => {
    items()[0]?.focus();
    setStatus("Prêt — navigation télécommande activée.");
  });
})();
