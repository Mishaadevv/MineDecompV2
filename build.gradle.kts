plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
}

allprojects {
    group = "com.minedecomp"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://libraries.minecraft.net")
        maven("https://maven.minecraftforge.net")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        "implementation"(kotlin("stdlib"))
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}
