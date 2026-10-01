plugins {
    application
}

application {
    mainClass.set("com.minedecomp.cli.MainKt")
    // Modern clients (30k+ classes) OOM smaller heaps during decompilation.
    applicationDefaultJvmArgs = listOf("-Xmx4g")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":app"))
    implementation(project(":mappings-providers:mcpconfig"))
    implementation(project(":mappings-providers:mojang"))
    implementation(project(":mappings-providers:yarn"))
    implementation(project(":mappings-providers:mcpnew"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
