const assert = require("assert");
const fs = require("fs");

const html = fs.readFileSync("tizenApp/index.html", "utf8");
const app = fs.readFileSync("tizenApp/app.js", "utf8");
const provider = fs.readFileSync("tizenApp/provider.js", "utf8");

const order = [
  'id="continue-shelf"',
  'id="next-episodes-shelf"',
  'id="recent-channels-shelf"',
  'id="favorites-shelf"',
  'id="recent-movies-shelf"',
  'id="recent-series-shelf"',
  'id="same-category-shelf"',
];

let previous = -1;
for (const marker of order) {
  const index = html.indexOf(marker);
  assert(index > previous, "canonical Home shelf order must be preserved: " + marker);
  previous = index;
}

assert(!app.includes('Films indisponibles pour cette playlist M3U.'), "M3U Movies must not be blocked");
assert(!app.includes('Séries indisponibles pour cette playlist M3U.'), "M3U Series must not be blocked");
assert(provider.includes('if (config.type === "m3u") return (await loadM3uCatalog(config)).movies'), "M3U Movies must be supported");
assert(provider.includes('if (config.type === "m3u")'), "M3U provider branches must exist");
assert(provider.includes("loadM3uSeriesInfo"), "M3U Series details must be supported");
assert(app.includes('data-home-see-all'), "Home must support See All");
assert(app.includes('buildNextEpisodes'), "Home must build Next Episodes");
assert(app.includes('lastViewedCatalogItem'), "Home must build same-category recommendations");

console.log("Tizen canonical Home + full M3U parity tests passed");
