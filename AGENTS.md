Always use the /caveman skill from skills/caveman/SKILL.md

# Agent Operating Guidelines

## 1. Documentation & Wiki Topology
- **Main Repository (`docs/`)**: All technical reconnaissance reports, ad-layer Smali documentation (`docs/ad-layer-*.md`, `docs/vk-video-ad-surface.md`), and DSL reference guides (`docs/revanced-v22-dsl-reference.md`) live in `docs/` inside `byDimasik/vk-video-revanced-patches` so each Google Jules task produces a single self-contained PR.
- **Wiki Repository (`byDimasik/vk-video-revanced-patches-wiki`)**: Maintained by Gemini Spark for cross-session PM state (`ROADMAP.md`), Architecture Decision Records (`decisions/`), and initial analogue research (`research/`). **Read-only for Google Jules** (see `skills/working-with-project-wiki/SKILL.md`). Never attempt to push commits or open PRs to the wiki repository from a Jules task.

## 2. Pull Request Scope & Lifecycle
- Use the `pr-lifecycle` skill (`skills/pr-lifecycle/SKILL.md`) for managing pull requests.
- Keep PRs atomic: one task, patch, or recon document per PR with corresponding unit tests where applicable.
- Never open multi-feature PRs or perform work outside the assigned issue scope.

## 3. Technology Target & Binary Policy
- Target ReVanced Patcher v22.0.1 Kotlin DSL (`bytecodePatch`, `resourcePatch`, `fingerprint`).
- Target APK `com.vk.vkvideo` is cached in `.cache/` via `make fetch-apk` and tracked in `version.mk`.
- Never commit `.apk`, `.dex`, `.rvp`, or raw decompiled `.smali` trees to git.

## 4. Injected-Smali Rules (hard-won, device-verified)
- Growing a method's register frame moves the incoming-argument window: DEX places arguments in the LAST `paramWidth` registers of the frame, so ANY registerCount growth must remap the parameter window (+1 per grown slot) — `growFrameWithScratch`/`prependConditionalReturn` do this. Appending registers without remapping reads every parameter one register low (ART `Undefined` VerifyError; JVM tests cannot catch it).
- R8 does not retain `kotlin/Unit` in this app — injected `Function0`/`Function1` callbacks must end `const/4 v0, 0x0` + `return-object v0`, never `sget-object Lkotlin/Unit;` (device crash: `NoClassDefFoundError: Lkotlin/Unit;`).
- Field opcodes (`iget-object`, `iput*`) are format 22c — 4-bit registers, no `/range` form. Scratch logic that touches fields must live in an injected helper class, not in appended high-register blocks.
- Check `logcat -b crash` after EVERY interaction on device, not only after cold start — a tap-triggered crash (e.g. the Unit case above) otherwise masquerades as a navigation bug.
