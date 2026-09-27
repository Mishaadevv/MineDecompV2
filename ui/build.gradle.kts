plugins {
    id("org.openjfx.javafxplugin")
    application
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("com.minedecomp.ui.MineDecompApp")
}

dependencies {
    implementation(project(":app"))
    implementation(project(":core"))
    implementation(project(":mappings-providers:mcpconfig"))
    implementation(project(":mappings-providers:mojang"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
    implementation("org.openjfx:javafx-graphics:21")
    implementation("com.google.code.gson:gson:2.10.1")
}
