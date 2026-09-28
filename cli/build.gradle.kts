plugins {
    application
}

application {
    mainClass.set("com.minedecomp.cli.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":app"))
    implementation(project(":mappings-providers:mcpconfig"))
    implementation(project(":mappings-providers:mojang"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
