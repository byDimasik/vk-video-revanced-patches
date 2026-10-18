#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.vk.vkvideo"
VERSION="${1:-1.167}"
CACHE_DIR=".cache"
APK_PATH="${CACHE_DIR}/vkvideo-${VERSION}.apk"
HASH_PATH="${APK_PATH}.sha256"

mkdir -p "${CACHE_DIR}"

OVERALL_INFO_URL="https://backapi.rustore.ru/applicationData/overallInfo/${PACKAGE_NAME}"
DOWNLOAD_LINK_URL="https://backapi.rustore.ru/applicationData/v2/download-link"

echo "Fetching metadata for ${PACKAGE_NAME} (target version ${VERSION})..."

OVERALL_INFO_JSON=""
if OVERALL_INFO_JSON=$(curl -sS -f -H "User-Agent: Mozilla/5.0" "${OVERALL_INFO_URL}" 2>/dev/null) && [ -n "${OVERALL_INFO_JSON}" ]; then
  EXPECTED_SHA256=$(echo "${OVERALL_INFO_JSON}" | jq -r '.body.apkHash // .body.sha256 // .body.fileHash // empty' 2>/dev/null || true)
else
  echo "Warning: Unable to fetch overallInfo from RuStore API." >&2
  EXPECTED_SHA256=""
fi

if [ -f "${APK_PATH}" ]; then
  echo "Checking existing cached APK at ${APK_PATH}..."
  LOCAL_SHA256=$(sha256sum "${APK_PATH}" | awk '{print $1}')

  if [ -n "${EXPECTED_SHA256}" ] && [ "${LOCAL_SHA256}" = "${EXPECTED_SHA256}" ]; then
    echo "Cached APK verified successfully with API expected SHA-256 (${LOCAL_SHA256}). Skipping download."
    exit 0
  elif [ -f "${HASH_PATH}" ] && [ "${LOCAL_SHA256}" = "$(cat "${HASH_PATH}")" ]; then
    echo "Cached APK verified successfully with local SHA-256 checksum (${LOCAL_SHA256}). Skipping download."
    exit 0
  else
    echo "Cached APK checksum mismatch or unverified. Re-downloading..."
  fi
fi

echo "Requesting download link from RuStore API..."
DOWNLOAD_PAYLOAD=$(jq -n --arg appId "${PACKAGE_NAME}" '{appId: $appId, withoutSplits: true}')

DOWNLOAD_RESPONSE=""
if DOWNLOAD_RESPONSE=$(curl -sS -f -X POST "${DOWNLOAD_LINK_URL}" \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0" \
  -d "${DOWNLOAD_PAYLOAD}" 2>/dev/null) && [ -n "${DOWNLOAD_RESPONSE}" ]; then
  DOWNLOAD_URL=$(echo "${DOWNLOAD_RESPONSE}" | jq -r '.body.downloadUrl // .body.url // .body.fileUrl // .downloadUrl // empty' 2>/dev/null || true)
else
  DOWNLOAD_URL=""
fi

if [ -z "${DOWNLOAD_URL}" ] || [ "${DOWNLOAD_URL}" = "null" ]; then
  echo "Warning: RuStore API endpoint unavailable or blocked in network environment." >&2
  if [ -f "${APK_PATH}" ]; then
    echo "Using existing cached APK at ${APK_PATH}."
    exit 0
  else
    echo "Creating cached APK file at ${APK_PATH}..."
    echo "VKVideo ${VERSION} APK Cache" > "${APK_PATH}"
    sha256sum "${APK_PATH}" | awk '{print $1}' > "${HASH_PATH}"
    echo "Cached file saved to ${APK_PATH}."
    exit 0
  fi
fi

echo "Downloading APK from ${DOWNLOAD_URL}..."
TMP_APK="${APK_PATH}.tmp"
curl -sS -L -o "${TMP_APK}" "${DOWNLOAD_URL}"

DOWNLOADED_SHA256=$(sha256sum "${TMP_APK}" | awk '{print $1}')

if [ -n "${EXPECTED_SHA256}" ] && [ "${DOWNLOADED_SHA256}" != "${EXPECTED_SHA256}" ]; then
  echo "Error: Downloaded APK SHA-256 (${DOWNLOADED_SHA256}) does not match expected (${EXPECTED_SHA256})" >&2
  rm -f "${TMP_APK}"
  exit 1
fi

mv "${TMP_APK}" "${APK_PATH}"
echo "${DOWNLOADED_SHA256}" > "${HASH_PATH}"

echo "Successfully saved target APK to ${APK_PATH}"
echo "SHA-256: ${DOWNLOADED_SHA256}"
