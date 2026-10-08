const assert = require("assert");
const fs = require("fs");
const source = fs.readFileSync("tizenApp/app.js", "utf8");

assert(source.includes("getDuid"), "Samsung DUID should be used when available");
assert(source.includes('return "tizen-duid-" + stableHash("zyviotv-player:" + rawDuid)'), "DUID must be transformed before use");
assert(!source.includes('return "tizen-session"'), "shared tizen-session fallback must not remain");
assert(source.includes("return hardwareUid || randomDeviceUid()"), "storage failure must still return a unique fallback");
assert(source.includes("Math.imul"), "stable local hash implementation is expected");

console.log("Tizen stable device UID tests passed");
