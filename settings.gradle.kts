pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "Lia-s-media-player"

// One source tree, one branch, N Minecraft versions.
// Each entry below becomes a subproject under versions/<name>, sharing build.gradle.kts
// and the root src/ tree. See PORTING.md for the target matrix.
stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.4", "1.21.5")
        vcsVersion = "1.21.1"
    }
}
