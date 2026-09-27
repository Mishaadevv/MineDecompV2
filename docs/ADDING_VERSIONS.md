# Добавление поддержки новой версии Minecraft

## Шаг 1: Добавьте версию в провайдер маппингов

Откройте `mappings-providers/mcpconfig/src/main/kotlin/com/minedecomp/mappings/mcpconfig/McpConfigProvider.kt`

Добавьте версию в `supportedVersions`:
```kotlin
private val supportedVersions = setOf(
    "1.12.2", "1.12.1", // ... существующие версии
    "1.11.2"  // новая версия
)
```

## Шаг 2: Проверьте наличие маппингов

Убедитесь, что для версии существуют маппинги в MCPConfig:
- Проверьте `https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_stable/`
- Или создайте собственный провайдер

## Шаг 3: Создание собственного провайдера (опционально)

Если стандартные маппинги недоступны, создайте новый провайдер:

1. Создайте модуль `mappings-providers/myprovider/`
2. Реализуйте `MappingProvider`:
```kotlin
class MyCustomProvider : MappingProvider {
    override val name = "MyProvider"
    
    override suspend fun supports(version: String): Boolean {
        return version == "1.11.2"
    }
    
    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings {
        // Загрузите и парсите маппинги
        return Mappings(
            version = version,
            jarType = jarType,
            classMappings = mapOf("a" => "net/minecraft/class1"),
            methodMappings = mapOf("a.a" => "method1"),
            fieldMappings = mapOf("a.b" => "field1")
        )
    }
}
```

3. Зарегистрируйте в `MineDecompApp.init()`:
```kotlin
mappingProviders = listOf(
    McpConfigProvider(cacheDir),
    MyCustomProvider()
)
```

## Шаг 4: Тестирование

1. Запустите приложение
2. Выберите новую версию в списке
3. Запустите декомпиляцию
4. Проверьте результат

## Форматы маппингов

### tsrg формат
```
net/minecraft/class1 net/minecraft/class2
    method1 method2
    field1 field2
```

### csv формат
```
searge,name,side,desc
method1,methodName,0,Description
```

## Примечания

- Версии выше 1.12.2 не поддерживаются из-за изменений в структуре байткода
- Для версий 1.13+ требуется дополнительная обработка из-за перехода на новую систему маппингов
