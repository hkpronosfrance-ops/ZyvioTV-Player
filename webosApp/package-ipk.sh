#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-${WEBOS_DEVICE_NAME:-}}"
RESOLUTION="${WEBOS_RESOLUTION:-1920x1080}"

case "${RESOLUTION}" in
  1920x1080|1280x720) ;;
  *)
    echo "Résolution webOS invalide: ${RESOLUTION}" >&2
    echo "Valeurs supportées: 1920x1080 ou 1280x720" >&2
    exit 1
    ;;
esac

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAFE_RES="${RESOLUTION/x/X}"
OUT_DIR="${APP_DIR}/dist/${SAFE_RES}"
STAGE_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "${STAGE_DIR}"
}
trap cleanup EXIT

if ! command -v ares-package >/dev/null 2>&1; then
  echo "webOS CLI introuvable." >&2
  echo "Installez-le avec : npm install -g @webos-tools/cli" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
rm -f "${OUT_DIR}"/*.ipk
cp -R "${APP_DIR}/." "${STAGE_DIR}/"
rm -rf "${STAGE_DIR}/dist"

node - "${STAGE_DIR}/appinfo.json" "${RESOLUTION}" <<'NODE'
const fs = require("fs");
const [path, resolution] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(path, "utf8"));
data.resolution = resolution;
fs.writeFileSync(path, JSON.stringify(data, null, 2) + "\n");
NODE

echo "==> Vérification appinfo.json (${RESOLUTION})"
ares-package --check "${STAGE_DIR}"

echo "==> Packaging IPK (${RESOLUTION})"
ares-package --outdir "${OUT_DIR}" "${STAGE_DIR}"

IPK="$(find "${OUT_DIR}" -maxdepth 1 -type f -name '*.ipk' -print -quit)"
if [[ -z "${IPK}" ]]; then
  echo "Aucun paquet .ipk généré dans ${OUT_DIR}." >&2
  exit 1
fi

echo "IPK: ${IPK}"
ares-package --info "${IPK}"

if [[ -n "${DEVICE}" ]]; then
  echo "==> Installation sur '${DEVICE}'"
  ares-install --device "${DEVICE}" "${IPK}"

  echo "==> Lancement de fr.zyviotv.player"
  ares-launch --device "${DEVICE}" fr.zyviotv.player
fi
