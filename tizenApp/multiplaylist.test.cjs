const assert = require("assert");
const fs = require("fs");

const source = fs.readFileSync("tizenApp/app.js", "utf8");

assert(
  source.includes('return String(playlistId || "") + ":" + String(type || "") + ":" + String(id || "")'),
  "libraryKey must include playlist + type + content"
);

assert(
  source.includes("libraryKey(item.playlist_id, item.content_type, item.content_id)"),
  "favorite/progress matching must use composite libraryKey"
);

assert(
  source.includes('String(item.playlist_id || "") === String(currentPlaylist?.id || "")'),
  "Home/library rendering must filter to active playlist"
);

assert(
  source.includes('throw new Error("Ce contenu appartient à une autre playlist.")'),
  "cross-playlist resume must be rejected"
);

console.log("Tizen multi-playlist library key tests passed");
