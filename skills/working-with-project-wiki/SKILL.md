---
name: working-with-project-wiki
description: >
  Use when instructed to record decisions, save research results, or read project documentation.
  Instructs to work directly on the main branch of the wiki repository.
---

# working-with-project-wiki

Use this skill whenever you need to record architectural decisions, document research results, or read shared project documentation.

**Core Principle: Commit directly to `main` on the wiki repository.**

The project wiki repository is: `https://github.com/byDimasik/vk-video-revanced-patches-wiki`.

## Rules

### 1. Direct Commits Only

Never create pull requests for the wiki repository. Always commit and push straight to the `main` branch.
When pushing to the repository, use the environment variables `GITHUB_ACTOR` and `GITHUB_TOKEN`, as they are always available in the Jules environment.

### 2. Respect Directory Structure

Files must be placed in their designated folders:
- Architecture Decision Records (ADRs) and recorded decisions go in `decisions/`.
- Findings, experiments, and exploration logs go in `research/`.

### 3. Read from Main

When instructed to read the wiki, read directly from the `main` branch of the wiki repository.

## Workflow Example

<Good>
```bash
git clone https://github.com/byDimasik/vk-video-revanced-patches-wiki
cd vk-video-revanced-patches-wiki
echo "# New Decision" > decisions/004-new-architecture.md
git add decisions/004-new-architecture.md
git commit -m "Record decision on new architecture"
git push origin main
```
</Good>

<Bad>
```bash
git checkout -b record-decision
echo "# New Decision" > decisions/004-new-architecture.md
git commit -am "Record decision"
git push origin record-decision
# Creating a Pull Request for the wiki is forbidden
```
</Bad>

## Red Flags - STOP and Correct Course

- Attempting to create a Pull Request for the wiki repo.
- Checking out a new branch in the wiki repo.
- Placing decision or research files in the root directory instead of `decisions/` or `research/`.
- Pushing to anywhere other than the `main` branch.
