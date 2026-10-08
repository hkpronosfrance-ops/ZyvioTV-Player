const assert = require("assert");
const fs = require("fs");
const app = fs.readFileSync("webosApp/app.js", "utf8");
const provider = fs.readFileSync("webosApp/provider.js", "utf8");

assert(!app.includes('Films indisponibles pour cette playlist M3U.'), "M3U Movies must not be blocked");
assert(!app.includes('Séries indisponibles pour cette playlist M3U.'), "M3U Series must not be blocked");
assert(!app.includes('providerConfig.type !== "xtream"'), "Home/Next Episodes must not be Xtream-only");
assert(app.includes("window.ZyvioProvider.loadSeriesInfo("), "Series details must use generic provider contract");
assert(provider.includes('if (config.type === "m3u") return (await loadM3uCatalog(config)).movies'), "M3U Movies must be exposed");
assert(provider.includes("loadM3uSeriesInfo"), "M3U Series detail support required");

console.log("webOS full M3U app contract tests passed");
