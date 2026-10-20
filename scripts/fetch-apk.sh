#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

mkdir -p "${REPO_ROOT}/.cache"

python3 - << 'EOF'
import os
import sys
import re
import urllib.request
import urllib.parse
import zipfile
import io
import hashlib
import subprocess
import base64

REPO_ROOT = os.environ.get("REPO_ROOT", os.getcwd())
CACHE_DIR = os.path.join(REPO_ROOT, ".cache")
VERSION_MK = os.path.join(REPO_ROOT, "version.mk")

os.makedirs(CACHE_DIR, exist_ok=True)

API_URL = "https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name=com.vk.vkvideo"
HEADERS = {
    "x-sv": "29",
    "x-abis": "arm64-v8a,armeabi-v7a,armeabi",
    "x-gp": "1",
    "User-Agent": "okhttp/3.12.1"
}

def parse_version_mk(filepath):
    if not os.path.exists(filepath):
        return None, None, None
    v_name, v_code, sha256 = None, None, None
    try:
        with open(filepath, "r") as f:
            for line in f:
                line = line.strip()
                if line.startswith("TARGET_VERSION :="):
                    v_name = line.split(":=")[1].strip()
                elif line.startswith("TARGET_VERSION_CODE :="):
                    v_code = line.split(":=")[1].strip()
                elif line.startswith("TARGET_SHA256 :="):
                    sha256 = line.split(":=")[1].strip()
    except Exception:
        pass
    return v_name, v_code, sha256

def validate_apk_bytes(apk_bytes):
    if not apk_bytes or len(apk_bytes) < 1000:
        return False, "APK payload too small"
    if not zipfile.is_zipfile(io.BytesIO(apk_bytes)):
        return False, "Payload is not a valid ZIP file"
    try:
        zf = zipfile.ZipFile(io.BytesIO(apk_bytes))
        namelist = zf.namelist()
        if "AndroidManifest.xml" not in namelist:
            return False, "ZIP does not contain AndroidManifest.xml at root"
        if "classes.dex" not in namelist:
            return False, "ZIP does not contain classes.dex at root"
        return True, "Valid APK"
    except Exception as e:
        return False, f"ZIP validation error: {e}"

def get_apk_badging(apk_path):
    aapt_paths = [
        "/opt/android-sdk/build-tools/34.0.0/aapt",
        "/opt/android-sdk/build-tools/35.0.0/aapt",
        "/opt/android-sdk/build-tools/33.0.2/aapt",
        "aapt"
    ]
    aapt_bin = None
    for p in aapt_paths:
        if os.path.exists(p) or subprocess.run(["which", p], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            aapt_bin = p
            break
    if not aapt_bin:
        return None, None, None
    try:
        out = subprocess.check_output([aapt_bin, "dump", "badging", apk_path], text=True, stderr=subprocess.DEVNULL)
        match = re.search(r"package:\s+name=\x27([^\x27]+)\x27\s+versionCode=\x27([^\x27]+)\x27\s+versionName=\x27([^\x27]+)\x27", out)
        if match:
            return match.group(1), match.group(3), match.group(2)
    except Exception:
        pass
    return None, None, None

# Parse API response with explicit timeout
print("Querying APKPure API for com.vk.vkvideo...")
req = urllib.request.Request(API_URL, headers=HEADERS)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        api_data = resp.read()
except Exception as e:
    print(f"Error querying API: {e}", file=sys.stderr)
    sys.exit(1)

matches = re.findall(rb"https?://download\.pureapk\.com/b/(?:APK|XAPK)/[^\s\x27\x22\x00-\x1f\x7f-\xff]+", api_data)
if not matches:
    print("Error: Could not resolve download URL from API response", file=sys.stderr)
    sys.exit(1)

match_urls = [m.decode() for m in matches]
standalone_urls = [u for u in match_urls if "/b/APK/" in u]
xapk_urls = [u for u in match_urls if "/b/APK/" not in u]
candidate_urls = standalone_urls + xapk_urls

download_url = candidate_urls[0]
version_str, version_code_str = None, None

parsed_url = urllib.parse.urlparse(download_url)
query_params = urllib.parse.parse_qs(parsed_url.query)
c_param = query_params.get("c", [""])[0]
fn_param = query_params.get("_fn", [""])[0]

if "|" in c_param:
    parts = c_param.split("|")
    b64_part = re.sub(r"[^a-zA-Z0-9+/=]", "", parts[-1])
    try:
        decoded = base64.b64decode(b64_part + "==").decode("utf-8", errors="ignore")
        vn_m = re.search(r"vn=([0-9]+\.[0-9]+(?:\.[0-9]+)?)", decoded)
        vc_m = re.search(r"vc=([0-9]+)", decoded)
        if vn_m: version_str = vn_m.group(1)
        if vc_m: version_code_str = vc_m.group(1)
    except Exception:
        pass

if not version_str:
    fn_m = re.search(r"v([0-9]+\.[0-9]+(?:\.[0-9]+)?)", fn_param)
    if fn_m: version_str = fn_m.group(1)

recorded_vname, recorded_vcode, recorded_sha256 = parse_version_mk(VERSION_MK)

if version_str:
    candidate_cache_path = os.path.join(CACHE_DIR, f"vkvideo-{version_str}.apk")
    if os.path.exists(candidate_cache_path) and os.path.getsize(candidate_cache_path) > 0:
        with open(candidate_cache_path, "rb") as f:
            cached_bytes = f.read()
        cached_sha256 = hashlib.sha256(cached_bytes).hexdigest()
        is_valid, err_msg = validate_apk_bytes(cached_bytes)

        if is_valid and recorded_sha256 and cached_sha256 == recorded_sha256:
            pkg_name, badging_vname, badging_vcode = get_apk_badging(candidate_cache_path)
            if pkg_name != "com.vk.vkvideo":
                print(f"Cached APK badging package verification failed: got '{pkg_name}', expected 'com.vk.vkvideo'. Reacquiring...", file=sys.stderr)
            else:
                v_name = badging_vname or version_str
                v_code = badging_vcode or version_code_str or recorded_vcode
                print(f"Cached APK found and verified at {candidate_cache_path}")
                print(f"SHA-256: {cached_sha256}")
                print(f"Verified idempotently against recorded checksum and package identity.")
                sys.exit(0)

successful_apk_bytes = None
obtained_vname = None
obtained_vcode = None

for idx, url in enumerate(candidate_urls):
    print(f"Attempting download candidate {idx+1}/{len(candidate_urls)}: {url[:80]}...")
    dl_req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(dl_req, timeout=60) as dl_resp:
            raw_payload = dl_resp.read()
    except Exception as e:
        print(f"Failed download from {url[:50]}: {e}", file=sys.stderr)
        continue

    if not zipfile.is_zipfile(io.BytesIO(raw_payload)):
        print(f"Candidate {idx+1} is not a valid ZIP file. Skipping...", file=sys.stderr)
        continue

    zf = zipfile.ZipFile(io.BytesIO(raw_payload))
    namelist = zf.namelist()

    extracted_bytes = None
    if "AndroidManifest.xml" in namelist and "classes.dex" in namelist:
        # Standalone single APK
        extracted_bytes = raw_payload
        print(f"Candidate {idx+1} is a standalone APK.")
    else:
        # XAPK archive - check for split entries
        apk_entries = [n for n in namelist if n.endswith(".apk")]
        split_entries = [n for n in apk_entries if os.path.basename(n).startswith("config.")]
        if split_entries or len(apk_entries) != 1:
            print(f"Candidate {idx+1} XAPK is not a single-APK container ({apk_entries}). Rejecting...", file=sys.stderr)
            continue

        target_entry = apk_entries[0]
        extracted_bytes = zf.read(target_entry)
        print(f"Extracted '{target_entry}' from single-APK XAPK container.")

    if not extracted_bytes:
        continue

    is_valid, validation_err = validate_apk_bytes(extracted_bytes)
    if not is_valid:
        print(f"Candidate {idx+1} APK validation failed: {validation_err}", file=sys.stderr)
        continue

    # Verify package name via badging
    temp_apk_path = os.path.join(CACHE_DIR, f"temp_{idx}.apk")
    with open(temp_apk_path, "wb") as f:
        f.write(extracted_bytes)

    pkg_name, badging_vname, badging_vcode = get_apk_badging(temp_apk_path)
    os.remove(temp_apk_path)

    if not pkg_name:
        print(f"Error: Could not read APK badging (aapt missing or invalid manifest)", file=sys.stderr)
        sys.exit(1)
    if pkg_name != "com.vk.vkvideo":
        print(f"Candidate {idx+1} package name is '{pkg_name}', expected 'com.vk.vkvideo'. Skipping...", file=sys.stderr)
        continue

    successful_apk_bytes = extracted_bytes
    obtained_vname = badging_vname or version_str
    obtained_vcode = badging_vcode or version_code_str
    break

if not successful_apk_bytes or not obtained_vname or not obtained_vcode:
    print("Error: Failed to obtain a valid single non-split com.vk.vkvideo APK from API download candidates", file=sys.stderr)
    sys.exit(1)

target_apk_filename = f"vkvideo-{obtained_vname}.apk"
target_apk_path = os.path.join(CACHE_DIR, target_apk_filename)
with open(target_apk_path, "wb") as f:
    f.write(successful_apk_bytes)

final_sha256 = hashlib.sha256(successful_apk_bytes).hexdigest()

print(f"Target APK saved: {target_apk_path}")
print(f"APK badging: versionName={obtained_vname}, versionCode={obtained_vcode}")
print(f"SHA-256: {final_sha256}")

with open(VERSION_MK, "w") as f:
    f.write(f"# Auto-generated by scripts/fetch-apk.sh. Do not edit manually.\n")
    f.write(f"TARGET_VERSION := {obtained_vname}\n")
    f.write(f"TARGET_VERSION_CODE := {obtained_vcode}\n")
    f.write(f"TARGET_SHA256 := {final_sha256}\n")

print(f"Successfully generated {VERSION_MK}")
EOF
