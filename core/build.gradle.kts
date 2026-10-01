dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
    implementation("net.minecraftforge:srgutils:0.4.1")
    implementation("org.vineflower:vineflower:1.12.0")
    implementation("org.benf:cfr:0.152")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation(project(":mappings-providers:mcpconfig"))
    testImplementation(project(":mappings-providers:mojang"))
    testImplementation(project(":mappings-providers:yarn"))
    testImplementation(project(":mappings-providers:mcpnew"))
}

tasks.test {
    useJUnitPlatform()
    // End-to-end decompiles need heap: 1.14+ clients OOM the 512m default.
    // (8 GB dev machine: 4g worker + daemon fits; bigger versions swap.)
    maxHeapSize = "4g"
    systemProperty("e2eVersion", System.getProperty("e2eVersion", "1.7.10"))
    systemProperty("e2eKeyFile", System.getProperty("e2eKeyFile", "Minecraft.java"))
    systemProperty("e2eKeySnippet", System.getProperty("e2eKeySnippet", "class Minecraft"))
    systemProperty("e2eMinClasses", System.getProperty("e2eMinClasses", "1000"))
}
