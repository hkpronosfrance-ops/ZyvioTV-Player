"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = __dirname;
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf8");
const app = fs.readFileSync(path.join(root, "app.js"), "utf8");
for (const screen of ["auth-screen", "profile-screen", "pin-screen", "app-shell", "live-panel", "catalog-panel", "devices-panel"]) {
  assert.match(html, new RegExp('id="' + screen + '"'), screen + " must remain available");
}
for (const section of ["home", "live", "movies", "series", "profiles", "more"]) {
  assert.match(html, new RegExp('data-section="' + section + '"'), section + " navigation missing");
}
for (const source of ["auth.js", "cloud.js", "provider.js", "player.js", "app.js"]) {
  assert.ok(html.includes('src="' + source + '"'), source + " script must be retained");
}
assert.match(css, /--red:#E0102F/, "D6 main accent");
assert.match(css, /--ring:#F4F4F6/, "white TV focus");
assert.match(css, /min-width:2560px/, "4K layout");
assert.match(css, /max-width:1280px/, "720p layout");
assert.match(css, /prefers-reduced-motion:reduce/, "accessible motion");
assert.match(app, /document\.activeElement\?\.tagName/, "remote keys must not override typing");
assert.match(app, /item\.setAttribute\("aria-current", "page"\)/, "active navigation needs semantic state");
assert.match(app, /window\.ZyvioProvider\.loadLive/, "live provider preserved");
assert.match(app, /window\.ZyvioProvider\.loadMovies/, "movies provider preserved");
assert.match(app, /window\.ZyvioProvider\.loadSeries/, "series provider preserved");
console.log("LG webOS D6 structure and provider regression contracts OK");
