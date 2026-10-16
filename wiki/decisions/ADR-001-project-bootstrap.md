# ADR-001: Project Bootstrap, Team Topology, and Tooling

* **Status**: Accepted
* **Date**: 2026-10-16
* **Deciders**: Human Lead, Gemini Spark

## 1. Context & Mission

Build, test, and release an open-source [ReVanced Patcher v22](https://github.com/ReVanced/revanced-patcher) patch suite (`.rvp`) for the standalone VK Video Android client (`com.vk.vkvideo`).
The project is executed asynchronously from mobile at $0 additional infrastructure cost beyond the base Google AI Pro tier.

## 2. Multi-Agent Team Topology & Role Boundaries

| Role | Actor / Tool | Execution Environment | Core Responsibilities | Hard Boundaries (Never Do) |
| :--- | :--- | :--- | :--- | :--- |
| **Lead / Architect** | Human Lead | Mobile (Async) | High-level direction, architectural sign-off, PR merge approval, on-device smoke testing. | Never writes boilerplate, manually edits Smali, or babysits synchronous sessions. |
| **PM & Researcher** | Gemini Spark | Cloud VM + GitHub MCP | Upstream ReVanced research, issue decomposition, task labeling, PR review orchestration, light config/doc authoring. | Cannot run heavy Linux binaries (`./gradlew check`, `apktool`, `jadx`). Must delegate build/decompilation runs. |
| **Software Engineer** | Google Jules | Ephemeral Ubuntu VM (Bash, JDK, Gradle, Git, apktool, jadx) | Triggered via `jules` issue label. Decompiles target APKs, writes Kotlin DSL patches & unit tests, verifies `./gradlew check`. | Never opens multi-feature mega-PRs (strictly 1 atomic issue = 1 PR). Never commits raw APK/DEX binaries to git. |
| **Independent Reviewer** | CodeRabbit | Automated PR Analyzer (Claude/OpenAI + ast-grep) | Automated line-by-line review on every PR. Enforces Kotlin idioms, Smali register safety, and test coverage. | Advisory and gating only; does not push commits directly without explicit trigger. |
| **CI/CD & Release** | GitHub Actions (GHA) | Public Ubuntu Runners | Deterministic `./gradlew check` on PRs, compiles signed `.rvp` patch bundles, publishes releases for ReVanced Manager. | No non-deterministic AI loops inside GHA. |

## 3. Technology Stack & Licensing

* **Framework**: [ReVanced Patcher v22.0.1](https://github.com/ReVanced/revanced-patcher/releases/tag/v22.0.1) Kotlin DSL (`bytecodePatch`, `resourcePatch`, `fingerprint`).
* **License**: [GNU General Public License v3.0 (GPLv3)](https://www.gnu.org/licenses/gpl-3.0.en.html), matching upstream ReVanced ecosystem requirements.
* **Target Package**: `com.vk.vkvideo` acquired deterministically via unauthenticated RuStore API.
* **Artifact Policy**: Release `.rvp` patch bundles only. Never distribute patched or raw APK files.

## 4. Decision Log: Evaluated & Rejected Alternatives

* **Google Antigravity IDE / CLI**: Rejected. Requires desktop environment and non-Pro billing tiers.
* **GitHub Cloud Copilot Agent / PR Review**: Rejected. Paid feature incurring unnecessary subscription and token burn.
* **Private GitHub Repository**: Rejected. Caps GHA minutes and restricts free CodeRabbit OSS Pro tier.
* **Synchronous Development**: Rejected. Incompatible with asynchronous mobile-driven workflow.

## 5. Rate Limits & Guardrails

* **Jules**: 1 atomic issue = 1 focused PR (< 15 min VM run).
* **CodeRabbit**: Exclude decompiled/binary paths in `.coderabbit.yaml`; limit PR pace (≤ 3 PR burst, ≤ 4 PRs/hour).
* **GitHub Actions**: Cache Gradle dependencies; zero binary payloads committed to git history.
