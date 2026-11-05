---
name: upgrading-base-app
description: Use when a new VK Video base-app version must be supported — pull the new APK set, re-run patches, fix fingerprint drift, defeat the native tamper check, and validate on a real device before committing.
---

# Upgrading the Base App Version

## Overview

Steps to move the patch target to a newer VK Video release. The upgrade is only "done" after a
patched build survives cold relaunches on a real device. Key rule: **never ship a build that was
not cold-launch tested via adb**.

## When to Use

- A new VK Video version is installed on a connected device (USB or tailnet adb) and must become
  the new patch target.
- Patches fail to match fingerprints after an app update.

## Pipeline

1. **Acquire the full split set** from a device that has the new version:
   ```
   adb shell pm path com.vk.vkvideo          # list base + split_config.* apks
   adb pull <each path>
   ```
   Prefer a device pull over mirrors: apkpure serves base-only (no arm64 libs) unless signed URLs,
   RuStore/apkcombo endpoints rot. The base alone is NOT installable on 64-bit-only phones.

2. **Run the ReVanced CLI** (from repo root, tools/ live in the project parent):
   ```
   java -Xmx4g -jar tools/revanced-cli-6.0.0-all.jar patch \
     -p tools/vk-video-patches.rvp -b -o out/patched-<ver>.apk <base.apk>
   ```
   - Flag is `-p`, not `--patches`; `-b` bypasses signature verification of the input APK.
   - RAM: the host has 15 GB and no swap — add swap first (`fallocate 8G /swapfile; mkswap; swapon`)
     or the JVM gets OOM-killed (exit 137) during "Decoding resources".
   - Any `SEVERE: ... failed` line = a fingerprint that no longer matches. Fix before proceeding.

3. **Fix fingerprint drift**: strings anchors survive renames best. Baksmali the dexes that contain
   the anchor strings (the CLI jar embeds baksmali:
   `java -cp tools/revanced-cli-6.0.0-all.jar com.android.tools.smali.baksmali.Main disassemble ...`)
   and compare method shapes between old and new versions. Loosen over-specified fingerprints
   (drop params/strings that moved) but keep at least one unique stable string anchor.
   Rebuild the bundle: `ANDROID_HOME=~/android ./gradlew rvp`, then copy to `tools/`.

4. **Build the distributable as a MERGED-APK split set** (do NOT ship the CLI's apktool-rebuilt
   resources — they corrupt: spurious `@null` refs, wrong XML resource-id refs):
   - `base` = original base.apk entries + CLI-patched dexes (ALL of them — the CLI may rebuild
     into more dexes than the original had; iterate the CLI zip for `classes*.dex`, not the
     original) + CLI `AndroidManifest.xml` + CLI-patched target layouts (top shelf).
   - `split_config.arm64_v8a / en / xxhdpi` = original splits as-is.
   - Install with `adb install-multiple`. The split set must share one signature — re-sign each.

5. **Permission clashes on re-sign**: if another VK app (e.g. com.vk.im) installed later owns
   shared permissions (`com.vkontakte.android.permission.APP_REDIRECT`, `ACCESS_DATA`,
   `MAPS_RECEIVE`, `C2D_MESSAGE`), our re-signed build fails with
   `INSTALL_FAILED_DUPLICATE_PERMISSION` — even the unpatched app would. Fix: delete the
   `<permission>` **declarations** (keep `uses-permission`) from the patched manifest. Decode with
   `apktool d`, edit, `apktool b`, extract the rebuilt `AndroidManifest.xml` back into the merged
   base (never rebuild resources via apktool). Beware: an empty `${N}` in `sed -i "${N}d"` deletes
   the WHOLE file.

6. **Native tamper check (1.170+)**: `libvkcore.so` verifies the APK and calls `_exit(1)` on
   mismatch (log tag: "error! verify new libraries in application!"). Symptom: process "exited
   cleanly (1)" seconds after launch, no Java crash. Fix: patch the check helper to a bare `ret`
   (find the function referencing the message string in .rodata, NOP-free single-instruction patch
   at its entry), repack into the arm64 split, re-sign. Re-check this per new version — the check
   may change.

7. **Sign**: `zipalign -f 4` then
   `apksigner sign --ks <keystore> --ks-pass pass:... --ks-key-alias <alias> --min-sdk-version 28`.
   Verify with `apksigner verify --min-sdk-version 28` (default verify may fail on old-API digest
   checks — pass the flag).

8. **Device validation (required)**: uninstall stock, `adb install-multiple`, cold-launch
   `MainActivity` ≥3 times, each time: `pidof` non-empty after ~20 s, `logcat -b crash` free of
   new fatals, `uiautomator dump` shows the feed. Only then install to the user's phone.

9. **Repo updates**:
   - e2e tests resolve `.cache/vkvideo-*.apk` by newest-mtime — just add the new base snapshot
     to `.cache/`.
   - Update any version-pinned docs; fingerprints with comment notes about old versions stay as
     history.
   - Bump nothing else: patch compat is package-level, not version-level.

## Pitfalls

- CLI `--patches` doesn't exist; use `-p`. CLI arg order: `patch -p <bundle> -b -o <out> <apk>`.
- Java-written zips lack `META-INF/MANIFEST.MF` — sign with `--v1-signing-enabled false` or accept
  default v1+v2; both verified working.
- `ANDROID_HOME` must be set for `./gradlew rvp` or the bundle gets no `classes.dex`.
- /tmp is a per-user-quota tmpfs: `df` can show free space while writes still fail. Move big
  artifacts to /home (ext4).
- Pixel 8 Pro is 64-bit-only: armeabi-v7a-only libs → `INSTALL_FAILED_NO_MATCHING_ABIS`.
