# Архитектура MineDecompV2

## Обзор

MineDecompV2 — десктопный декомпилятор Minecraft для моддинга и образовательных целей. Построен на чистой архитектуре с разделением на слои.

## Слои

### Core (Ядро)
- **pipeline/** — этапы декомпиляции (DecompPipeline)
- **mappings/** — провайдеры маппингов (MappingProvider)
- **decompiler/** — интеграция Vineflower/CFR (DecompilerEngine)
- **cache/** — кэширование jar/маппингов (CacheManager)
- **Logging.kt** — логирование в файл (FileLogger)

### App (Оркестрация)
- **DecompService** — фоновый сервис декомпиляции
- **ProgressBus** — шина событий прогресса

### UI (JavaFX)
- **screens/** — экраны (MainView, SettingsView, ProgressView, ResultView)
- **MineDecompApp** — главный класс приложения

### Mappings Providers (цепочка: MCP → Mojang → Yarn → MCPConfig-new → Noop)
- **mcpconfig/** — классический MCP 1.6.4–1.12.2 (McpConfigProvider)
- **mcpnew/** — MCPConfig `joined.tsrg` + снапшоты 1.13–1.13.2 (McpNewProvider)
- **yarn/** — Fabric Yarn tiny-маппинги 1.14–1.14.3 + снапшоты (YarnMappingsProvider)
- **mojang/** — официальные маппинги Mojang 1.14.4–1.21.11 (MojangMappingsProvider)
- **core/mappings/NoopMappingsProvider** — obfuscated fallback для всего остального

## Пайплайн

1. **Download** — скачивание client.jar/server.jar с серверов Mojang
2. **Mappings** — загрузка маппингов MCP
3. **Remap** — деобфускация байткода через ASM
4. **Decompile** — декомпиляция через Vineflower/CFR
5. **Layout** — раскладка файлов по пакетам

## Ключевые интерфейсы

```kotlin
interface MappingProvider {
    val name: String
    suspend fun supports(version: String): Boolean
    suspend fun fetchMappings(version: String, jarType: JarType): Mappings
}

interface PipelineCallbacks {
    fun onEvent(event: PipelineEvent)
}
```

## Добавление нового провайдера маппингов

1. Реализуйте `MappingProvider`
2. Зарегистрируйте в `MineDecompApp.init()`

## Добавление поддержки новой версии

1. Добавьте версию в `McpConfigProvider.supportedVersions`
2. Убедитесь, что для версии существуют маппинги в MCPConfig
