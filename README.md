# MineDecompV2

Desktop Minecraft decompiler for modding and education, with a modern JavaFX UI.

MineDecompV2 automates the classic deobfuscation + decompilation pipeline for
Minecraft clients/servers (the same job MCP, MCPConfig and ForgeGradle userdev
do), without hand-written batch scripts:

1. Reads the Mojang version manifest (`version_manifest_v2.json`)
2. Downloads `client.jar` / `server.jar` + metadata for the chosen version
   (modern server jars are bundler wrappers — the inner game jar is extracted)
3. Downloads obfuscation mappings (pluggable providers, see below)
4. Remaps bytecode (classes/methods/fields) via ASM
5. Decompiles to sources — Vineflower 1.12 by default, CFR switchable
   (Vineflower emits `.kt` for classes with Kotlin metadata)
6. Lays out sources by package, optionally generates a Gradle skeleton
   (real library coordinates, Java toolchain and main class from version metadata)

## Supported versions

| Range | Mappings source | Status |
|---|---|---|
| 1.6.4 – 1.12.2 | MCP (`joined.srg` + `mcp_stable` CSV, or newest `mcp_snapshot` with snapshot channel) | verified end-to-end (1803 files for 1.7.10, 2050 for 1.12.2) |
| 1.13 – 1.13.2 | MCPConfig `joined.tsrg` + newest `mcp_snapshot` CSV from Forge maven | readable MCP names |
| 1.14 – 1.14.3 | Yarn (Fabric meta + maven, tiny mappings) | readable Yarn names |
| 1.14.4 – 1.21.11 | Mojang official mappings (ProGuard, from Mojang servers) | mappings download + parsing tested |
| snapshots (with Mojang/Yarn mappings) | Mojang official / Yarn, probed factually | hidden behind `Show snapshots` in UI, `--snapshots` in CLI |
| everything else: pre-1.0 era (Classic/Alpha/Beta) + 1.0–1.5.2, 26.x | none published — obfuscated fallback | orange `Obfuscated (no mappings)` badge, output keeps notch names; pre-1.6 versions ship client jar only, use Client side |

The main screen lists every release plus the pre-1.0 era (`old_beta` / `old_alpha`)
with a live provider badge
(`MCP`, `Mojang Official`, or greyed-out `no mappings`), plus search.
Snapshots are hidden from the list (700+ noisy entries) but decompile the same
way via CLI. Versions with the orange `Obfuscated (no mappings)` badge can still
be started — the output just keeps notch names.

## Requirements

- JDK 21 (pinned via `org.gradle.java.home` in `gradle.properties` —
  adjust the path to your own JDK 21+ installation)
- No system Gradle needed — use the wrapper (`gradlew.bat` on Windows)

## Build & run

```bat
gradlew.bat build
gradlew.bat :ui:run
```

Headless CLI (no GUI):

```bat
gradlew.bat :cli:run --args="--version 1.12.2"
gradlew.bat :cli:run --args="--version 1.7.10 --side both --decompiler cfr"
gradlew.bat :cli:run --args="--version 1.14.3 --mappings yarn"
gradlew.bat :cli:run --args="--version 1.12.2 --mcp-channel snapshot"
gradlew.bat :cli:run --args="--list-versions"
gradlew.bat :cli:run --args="--help"
```

`--side both` decompiles client and server sequentially into
`sources/<version>/client` and `sources/<version>/server` with one combined report.

Settings screen (and `--mappings` / `--mcp-channel` CLI flags) allow forcing
a mappings source (`auto` by default: MCP → Mojang → Yarn → MCPConfig 1.13 →
obfuscated) and switching MCP between `stable` and `snapshot` CSV channels.

Run tests:

```bat
gradlew.bat test
```

End-to-end decompilation test for one version (slow, downloads the real client):

```bat
gradlew.bat :core:test --tests "com.minedecomp.core.pipeline.EndToEndTest" -De2eVersion=1.7.10
```

## Architecture

```
MineDecompV2/
├── core/                    # Pipeline core (no UI knowledge)
│   ├── pipeline/            # DecompPipeline (stages)
│   ├── mappings/            # MappingProvider interface
│   ├── decompiler/          # Vineflower/CFR integration
│   └── cache/               # Download cache with retry + SHA1
├── app/                     # Orchestration
│   ├── DecompService        # Background service
│   ├── ProgressBus          # Progress events
│   └── AppSettings          # Persistent user settings
├── ui/                      # JavaFX interface
│   ├── screens/             # Main/Settings/Progress/Result
│   └── MineDecompApp        # Entry point
└── mappings-providers/      # Provider implementations
    ├── mcpconfig/           # MCP (Forge maven, 1.6.4–1.12.2)
    ├── mcpnew/              # MCPConfig joined.tsrg + snapshots (1.13–1.13.2)
    ├── yarn/                # Fabric Yarn tiny mappings (1.14–1.14.3, snapshots)
    └── mojang/              # Mojang official (piston-data, 1.14.4–1.21.11)
```

## Adding a new mappings source

1. Implement `MappingProvider`:
```kotlin
class MyCustomProvider : MappingProvider {
    override val name = "MyProvider"
    override suspend fun supports(version: String): Boolean = ...
    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings = ...
}
```
2. Register it in `MineDecompApp.createProviders()`
3. The version list picks it up automatically via `supports()`.

## Legal notice

This tool is intended for modding and education on a legally purchased copy
of Minecraft. It does NOT bundle or redistribute game files: `client.jar` /
`server.jar` are downloaded at runtime directly from Mojang's official servers,
exactly like the official Minecraft Launcher does. Mappings are fetched
dynamically from public repositories (Forge maven, Mojang piston-data) with
their sources attributed, never embedded in the distribution.

## License

MIT License
