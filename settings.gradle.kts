pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Picks the right Fabric Loom for each target. Minecraft 26+ ships unobfuscated,
    // and Fabric split the plugin in two to match: `fabric-loom-remap` for the
    // obfuscated versions, `fabric-loom` for the rest. This chooses between them, and
    // knows Stonecutter well enough to decide from the version alone. The Loom version
    // itself comes from `loomx.loom_version` in gradle.properties.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

rootProject.name = "Lia-s-media-player"

// One source tree, one branch, N Minecraft versions x 2 mod loaders.
//
// Each entry below becomes a subproject under versions/<mc>-<loader>, sharing the root
// src/ tree and the buildscript of its loader. Deliberately *not* Stonecutter branches:
// a branch gets its own source directory (versions/<branch>/src), which would duplicate
// the very thing this project keeps single. A flat tree with the Minecraft version
// declared separately keeps `stonecutter.eval` reasoning about the real version while
// the subproject name carries the loader.
//
// This is the only place the matrix is declared. The build workflow parses the match()
// lines straight out of this file, so keep them one per line and in this shape.
stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) = loaders.forEach {
            version("$version-$it", version).buildscript("build.$it.gradle.kts")
        }

        match("1.21.1", "neoforge", "fabric")
        match("1.21.4", "neoforge", "fabric")
        match("1.21.5", "neoforge", "fabric")
        match("1.21.8", "neoforge", "fabric")
        match("1.21.11", "neoforge", "fabric")
        match("26.1.2", "neoforge", "fabric")
        match("26.2", "neoforge", "fabric")

        vcsVersion = "1.21.1-neoforge"
    }
}
