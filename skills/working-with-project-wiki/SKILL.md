---
name: working-with-project-wiki
description: Use when reading project Architecture Decision Records (ADRs), analogue research, or writing technical/ad-layer documentation across the main repository and wiki repository.
---

# working-with-project-wiki

Defines the read/write boundary between the main code repository (`byDimasik/vk-video-revanced-patches`) and the project wiki repository (`byDimasik/vk-video-revanced-patches-wiki`).

## Core Rules

### 1. Role-Based Write Boundaries
- **Google Jules**: The wiki repository (`byDimasik/vk-video-revanced-patches-wiki`) is **READ-ONLY** for Jules. Because Jules operates on a single repository per task/PR, Jules must write all reconnaissance reports, ad-layer Smali analyses, and technical reference docs into `docs/` in the main repository (`byDimasik/vk-video-revanced-patches`) as part of its task PR.
- **Gemini Spark (PM)**: Owns direct commits to `main` on `byDimasik/vk-video-revanced-patches-wiki` (`ROADMAP.md`, `decisions/ADR-*.md`, `research/`).

### 2. Where Documentation Lives
- `byDimasik/vk-video-revanced-patches/docs/`:
  - Target APK ad-surface and per-layer Smali recon (`vk-video-ad-surface.md`, `ad-layer-video-player.md`, `ad-layer-feed-and-banners.md`, etc.)
  - ReVanced Patcher v22.0.1 DSL & test harness reference (`revanced-v22-dsl-reference.md`)
- `byDimasik/vk-video-revanced-patches-wiki` (Read-Only for Jules, Direct-to-`main` for Spark):
  - `ROADMAP.md`: Cross-session backlog and wave state
  - `decisions/`: Architecture Decision Records (`ADR-001`, `ADR-002`, `ADR-003`)
  - `research/`: Initial upstream analogue research (`analogue-patterns.md`)

## Red Flags - STOP and Correct Course
- **Jules attempting to clone and `git push` to `vk-video-revanced-patches-wiki`**: Forbidden. Commit technical and recon docs to `docs/` in the main repository branch instead.
- **Opening a Pull Request on `vk-video-revanced-patches-wiki`**: Forbidden. Spark commits directly to `main` on the wiki repository.
