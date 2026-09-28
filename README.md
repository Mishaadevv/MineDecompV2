# MineDecompV2

Desktop Minecraft decompiler for modding and education, with a modern JavaFX UI.

MineDecompV2 automates the classic deobfuscation + decompilation pipeline for
Minecraft clients/servers (the same job MCP, MCPConfig and ForgeGradle userdev
do), without hand-written batch scripts:

1. Reads the Mojang version manifest (`version_manifest_v2.json`)
2. Downloads `client.jar` / `server.jar` + metadata for the chosen version
3. Downloads obfuscation mappings (pluggable providers, see below)
4. Remaps bytecode (classes/methods/fields) via ASM
5. Decompiles to Java sources — Vineflower by default, CFR switchable
6. Lays out sources by package, optionally generates a Gradle skeleton

## Supported versions

| Range | Mappings source | Status |
|---|---|---|
| 1.6.4 – 1.12.2 | MCP (`joined.srg` + `mcp_stable` CSV from Forge maven; 1.6.4/1.7.2 searge-only) | verified end-to-end (1803 files for 1.7.10, 2050 for 1.12.2) |
| 1.14.4 – 1.21.x | Mojang official mappings (ProGuard, from Mojang servers) | mappings download + parsing tested |
| 1.13.x – 1.14.3, 26.x | — | greyed out in the UI: no published mappings exist |

The main screen lists every release with a live provider badge
(`MCP`, `Mojang Official`, or greyed-out `no mappings`), plus search.
Versions without mappings cannot be started.

## Requirements

- JDK 21 (pinned via `org.gradle.java.home` in `gradle.properties` —
  adjust the path to your own JDK 21+ installation)
- No system Gradle needed — use the wrapper (`gradlew.bat` on Windows)

## Build & run

```bat
gradlew.bat build
gradlew.bat :ui:run
```

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
    ├── mcpconfig/           # MCP (Forge maven, 1.7.10-1.12.2)
    └── mojang/              # Mojang official (piston-data, 1.14+)
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
