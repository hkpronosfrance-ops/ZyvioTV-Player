#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-${WEBOS_DEVICE_NAME:-}}"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${APP_DIR}/dist"

if ! command -v ares-package >/dev/null 2>&1; then
  echo "webOS CLI introuvable." >&2
  echo "Installez-le avec : npm install -g @webos-tools/cli" >&2
  exit 1
fi

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

echo "==> Vérification appinfo.json"
ares-package --check "${APP_DIR}"

echo "==> Packaging IPK"
ares-package --outdir "${OUT_DIR}" "${APP_DIR}"

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
