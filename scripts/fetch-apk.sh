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

# Parse API response
print("Querying APKPure API for com.vk.vkvideo...")
req = urllib.request.Request(API_URL, headers=HEADERS)
try:
    resp = urllib.request.urlopen(req)
    api_data = resp.read()
except Exception as e:
    print(f"Error querying API: {e}", file=sys.stderr)
    sys.exit(1)

matches = re.findall(rb"https?://download\.pureapk\.com/b/(?:APK|XAPK)/[^\s\x27\x22\x00-\x1f\x7f-\xff]+", api_data)
if not matches:
    print("Error: Could not resolve download URL from API response", file=sys.stderr)
    sys.exit(1)

download_url = matches[0].decode()

# Dynamically extract version & versionCode from API parameters
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

# Check existing cached file against version.mk SHA-256
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
            v_name = badging_vname or version_str
            v_code = badging_vcode or version_code_str or recorded_vcode
            if pkg_name and pkg_name != "com.vk.vkvideo":
                print(f"Cached APK package mismatch: {pkg_name}", file=sys.stderr)
            else:
                print(f"Cached APK found and verified at {candidate_cache_path}")
                print(f"SHA-256: {cached_sha256}")
                print(f"Verified idempotently against recorded checksum.")
                sys.exit(0)

print(f"Downloading target package from direct CDN URL...")
dl_req = urllib.request.Request(download_url, headers=HEADERS)
try:
    dl_resp = urllib.request.urlopen(dl_req)
    raw_payload = dl_resp.read()
except Exception as e:
    print(f"Error downloading payload: {e}", file=sys.stderr)
    sys.exit(1)

print(f"Downloaded payload size: {len(raw_payload)} bytes")

# Distinguish standalone APK vs XAPK container
apk_bytes = None

if zipfile.is_zipfile(io.BytesIO(raw_payload)):
    zf = zipfile.ZipFile(io.BytesIO(raw_payload))
    namelist = zf.namelist()

    if "AndroidManifest.xml" in namelist and "classes.dex" in namelist:
        # Payload is directly a standalone APK
        apk_bytes = raw_payload
        print("Payload verified directly as a standalone APK.")
    else:
        # Payload is an XAPK archive
        print("Payload is an XAPK archive. Extracting main APK...")
        target_entry = None
        for name in ["com.vk.vkvideo.apk", "base.apk"]:
            if name in namelist:
                target_entry = name
                break
        if not target_entry:
            for name in namelist:
                if name.endswith(".apk") and not name.startswith("config."):
                    target_entry = name
                    break
        if target_entry:
            extracted = zf.read(target_entry)
            is_valid_extracted, _ = validate_apk_bytes(extracted)
            if is_valid_extracted:
                apk_bytes = extracted
                print(f"Extracted main APK '{target_entry}' from XAPK container.")

if not apk_bytes:
    is_valid_raw, reason = validate_apk_bytes(raw_payload)
    if is_valid_raw:
        apk_bytes = raw_payload
    else:
        print(f"Error: Downloaded payload is not a valid APK ({reason})", file=sys.stderr)
        sys.exit(1)

# Validate APK before saving
is_valid, validation_err = validate_apk_bytes(apk_bytes)
if not is_valid:
    print(f"Error: APK validation failed: {validation_err}", file=sys.stderr)
    sys.exit(1)

# Get badging details from extracted APK
temp_apk_path = os.path.join(CACHE_DIR, "temp_download.apk")
with open(temp_apk_path, "wb") as f:
    f.write(apk_bytes)

pkg_name, badging_vname, badging_vcode = get_apk_badging(temp_apk_path)

if pkg_name and pkg_name != "com.vk.vkvideo":
    os.remove(temp_apk_path)
    print(f"Error: Downloaded APK package name is '{pkg_name}', expected 'com.vk.vkvideo'", file=sys.stderr)
    sys.exit(1)

final_vname = badging_vname or version_str
final_vcode = badging_vcode or version_code_str

if not final_vname or not final_vcode:
    os.remove(temp_apk_path)
    print("Error: Could not establish versionName or versionCode for APK", file=sys.stderr)
    sys.exit(1)

target_apk_filename = f"vkvideo-{final_vname}.apk"
target_apk_path = os.path.join(CACHE_DIR, target_apk_filename)
os.rename(temp_apk_path, target_apk_path)

final_sha256 = hashlib.sha256(apk_bytes).hexdigest()

print(f"Target APK saved: {target_apk_path}")
print(f"APK badging: versionName={final_vname}, versionCode={final_vcode}")
print(f"SHA-256: {final_sha256}")

with open(VERSION_MK, "w") as f:
    f.write(f"# Auto-generated by scripts/fetch-apk.sh. Do not edit manually.\n")
    f.write(f"TARGET_VERSION := {final_vname}\n")
    f.write(f"TARGET_VERSION_CODE := {final_vcode}\n")
    f.write(f"TARGET_SHA256 := {final_sha256}\n")

print(f"Successfully generated {VERSION_MK}")
EOF
