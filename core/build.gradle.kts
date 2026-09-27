dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
    implementation("net.minecraftforge:srgutils:0.4.1")
    implementation("org.vineflower:vineflower:1.10.1")
    implementation("org.benf:cfr:0.152")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation(project(":mappings-providers:mcpconfig"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("e2eVersion", System.getProperty("e2eVersion", "1.7.10"))
}
