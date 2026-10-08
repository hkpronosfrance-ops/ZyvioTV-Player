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

assert(app.includes('providerConfig.type !== "xtream"'), "Home catalog shelves must stay hidden for non-Xtream providers");
assert(provider.includes('if (!config || config.type !== "xtream") return [];'), "M3U Movies/Series must remain unsupported intentionally");
assert(app.includes('data-home-see-all'), "Home must support See All");
assert(app.includes('buildNextEpisodes'), "Home must build Next Episodes");
assert(app.includes('lastViewedCatalogItem'), "Home must build same-category recommendations");

console.log("Tizen canonical Home parity tests passed");
