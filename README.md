# VK Video ReVanced Patches

ReVanced patches for the standalone VK Video Android application (`com.vk.vkvideo`).

## Overview

This repository provides bytecode patches, resource patches, and extensions for VK Video built using the [ReVanced Patcher v22](https://github.com/ReVanced/revanced-patcher) Kotlin DSL.

Target distribution is ReVanced Patch Bundle (`.rvp`) format for use in ReVanced Manager.

## Documentation & Wiki

* [Project Wiki](wiki/Home.md)
  * [Architecture Decision Records (ADRs)](wiki/Decisions.md)
  * [ADR-001: Project Bootstrap, Team Topology, and Tooling](wiki/decisions/ADR-001-project-bootstrap.md)

## Features (In Development)

* Video player ad blocking (pre-roll, mid-roll short-circuiting)
* Feed and banner ad injection filtering
* Sponsored shelf and promotional card removal
* UI and player cleanup

## Architecture

* **Bytecode Patches**: Structural AST and opcode matching via ReVanced Fingerprints to mutate Dalvik bytecode instructions at build time.
* **Extensions**: Injected runtime helper classes compiled and merged into target APK DEX files.
* **Target Package**: `com.vk.vkvideo` (RuStore distribution).

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
