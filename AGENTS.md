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
