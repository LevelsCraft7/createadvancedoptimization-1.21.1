# Create: Advanced Optimization

Create: Advanced Optimization is a NeoForge server-side optimization and diagnostics mod for Create on Minecraft 1.21.1.

It targets Create `6.0.10` specifically and focuses on reducing Create-related TPS drops and lag spikes caused by large factories, processing chains, logistics, packages, trains, and contraptions while preserving normal gameplay behavior.

The current release is **version 1.3**.

## Target

- Mod version: `1.3`
- Minecraft: `1.21.1`
- Loader: `NeoForge`
- NeoForge: `21.1.233` or newer for Minecraft 1.21.1
- Java: `21`
- Create: exactly `6.0.10`
- Mod ID: `createadvancedoptimization`

## Server-side focus

This mod is intentionally server-oriented and focuses on server TPS, MSPT and Create-related processing hotspots.

It does not optimize:

- shaders
- rendering
- client FPS
- Embeddium
- Sodium
- Iris
- other purely graphical client features

## Version 1.3 features

- strict Create `6.0.10` compatibility guard
- server config with per-feature toggles
- lightweight profiler with `/createadvancedoptimization` and `/cao` commands
- Create diagnostics reports written to `logs/createadvancedoptimization/reports/` as unique `.txt` debriefs plus richer local `.html` reports
- `PackageEntity` monitoring with per-dimension tracking and chunk hotspots on demand
- conservative Diving Boots marker-write cleanup without persistent entity-side memoization
- bounded local Spout lookup cache for official Create filling recipe checks, enabled by default
- one-tick Basin and Mechanical Crafter recipe memoization with dimension-aware keys and nullable Crafter results
- belt funnel and deployer micro-optimizations for redundant stack-copy hot paths
- cache and optimization effectiveness counters in `/cao status` and `/cao stats`
- PackageEntity age, stationary-candidate and top-chunk diagnostics for logistics-heavy servers
- disabled-by-default experimental package configuration placeholder for future behavior work

## What the mod does not do

- no global "every other tick" throttling
- no forced slowdown of belts, funnels, fluids, machines, trains or contraptions
- no asynchronous access to live world data
- no redistribution, modification or bundling of the Create JAR
- no client-only configuration

## Installation

1. Install NeoForge for Minecraft `1.21.1`.
2. Install Create `6.0.10` separately.
3. Place `createadvancedoptimization-1.3.jar` in the server's `mods` folder.
4. Start the server and adjust the generated server configuration if needed.

Create is a required dependency. It is not included inside Create: Advanced Optimization and must be installed separately by the server or modpack owner.

## Building from source

The development environment retrieves Create `6.0.10` from the official Create Maven repository.

```bash
./gradlew build
```

The generated Create: Advanced Optimization JAR is written to `build/libs/`. The build dependency is used only for compilation and development runs. Create is not bundled into the finished mod JAR.

## Compatibility

- Designed for large NeoForge modpacks and dedicated servers.
- Requires Minecraft `1.21.1`, NeoForge and Create `6.0.10`.
- The mod targets Create APIs and methods directly.
- It does not intentionally manipulate classes belonging to unrelated third-party mods.
- Other mods that modify the same Create methods through Mixins may require compatibility testing.

## License and attribution

The source code of Create: Advanced Optimization is licensed under the **Mozilla Public License 2.0**, using the SPDX identifier `MPL-2.0`.

Copyright © 2026 LevelsFR.

You may use, study, modify and redistribute the covered source code under the terms of the MPL-2.0. When distributing the source code or modified covered files, you must preserve the applicable copyright and license notices and keep those covered files available under the MPL-2.0.

The MPL-2.0 applies at file level. It allows this project to be combined with files under other licenses, but modifications to MPL-covered files remain subject to the MPL-2.0 source-disclosure and notice requirements.

The license does not grant rights to the **Create: Advanced Optimization** name, logo, icons, artwork or project branding. Those elements remain Copyright © 2026 LevelsFR, All Rights Reserved, unless a file explicitly states otherwise.

See [`LICENSE`](LICENSE) for the complete license text and [`NOTICE`](NOTICE) for the project copyright and attribution notice.

## Experimental caution

Version 1.3 keeps risky behavior-changing ideas out of active optimization paths unless they are proven safe. Train, logistics, redstone-link and contraption hooks remain diagnostic-only unless a separate optimization is explicitly measured and enabled.

## Documentation

- `docs/configuration.md`
- `docs/commands.md`
- `CHANGELOG.md`
