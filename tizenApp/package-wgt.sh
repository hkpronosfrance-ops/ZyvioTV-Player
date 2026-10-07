#!/usr/bin/env bash
set -euo pipefail

PROFILE="${1:-${TIZEN_SECURITY_PROFILE:-}}"
DEVICE="${2:-${TIZEN_DEVICE_NAME:-}}"

if ! command -v tizen >/dev/null 2>&1; then
  echo "Tizen CLI introuvable. Installez Tizen Studio / CLI avant de continuer." >&2
  exit 1
fi

if [[ -z "${PROFILE}" ]]; then
  echo "Profil de signature manquant." >&2
  echo "Usage: bash tizenApp/package-wgt.sh <security-profile> [device-name]" >&2
  echo "ou définissez TIZEN_SECURITY_PROFILE." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/.buildResult"

rm -rf "${BUILD_DIR}"

echo "==> Build Tizen Web"
tizen build-web -- "${SCRIPT_DIR}"

echo "==> Package WGT signé avec le profil '${PROFILE}'"
tizen package -t wgt -s "${PROFILE}" -- "${BUILD_DIR}"

WGT="$(find "${BUILD_DIR}" -maxdepth 1 -type f -name '*.wgt' -print -quit)"
if [[ -z "${WGT}" ]]; then
  echo "Aucun fichier WGT généré dans ${BUILD_DIR}." >&2
  exit 1
fi

echo "WGT: ${WGT}"

if [[ -n "${DEVICE}" ]]; then
  echo "==> Installation sur '${DEVICE}'"
  tizen install -n "$(basename "${WGT}")" -t "${DEVICE}" -- "$(dirname "${WGT}")"
fi
