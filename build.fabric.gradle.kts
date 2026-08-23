plugins {
    id("java-library")
    id("maven-publish")
    id("dev.kikugie.stonecutter")
    // Applies Fabric Loom. Which Loom, though, depends on the target: Minecraft 26+
    // ships unobfuscated and Fabric split the plugin in two to match
    // (`fabric-loom-remap` for the obfuscated versions, `fabric-loom` for the rest).
    // This plugin picks between them from the Minecraft version, which it reads off
    // Stonecutter, and pins the version from `loomx.loom_version` in gradle.properties.
    id("dev.kikugie.loom-back-compat")
}

// The subproject is named "<mc>-fabric", so Stonecutter's default property tag is that
// whole string and the ["1.21.1"] tables in stonecutter.properties.toml would not be
// flattened. Declaring the Minecraft version as a tag restores that. See the matching
// comment in build.neoforge.gradle.kts for why the loader is not also a tag.
stonecutter.properties.tags(stonecutter.current.version)

// ---------------------------------------------------------------------------
// Properties
// ---------------------------------------------------------------------------
// Everything below comes from stonecutter.properties.toml, already flattened for the
// Minecraft version this subproject builds.
fun prop(name: String): String = project.property(name).toString()

val modId = prop("mod.id")
val modName = prop("mod.name")
val modLicense = prop("mod.license")
val modVersion = prop("mod.version")
val modGroup = prop("mod.group")
val apiVersion = prop("mod.api_version")

val minecraftVersion = prop("deps.minecraft")
val minecraftVersionRangeFabric = prop("deps.minecraft_range_fabric")
val fabricLoaderVersion = prop("deps.fabric_loader")
val fabricApiVersion = prop("deps.fabric_api")
val modMenuVersion = prop("deps.modmenu")
val javaVersion = prop("deps.java")

version = modVersion
group = modGroup

base {
    // Version- *and* loader-qualified: the same Minecraft version is built for both
    // loaders, so without the suffix the two jars would overwrite each other.
    archivesName = "$modId-$minecraftVersion-fabric"
}

sourceSets.main {
    // The NeoForge bridge imports net.neoforged, which is not on this classpath. Whole
    // files that belong to one loader are separated by package and excluded here rather
    // than wrapped in a `//? if fabric` guard: stonecutter.filters would not help, since
    // it only decides which files the preprocessor visits, not which ones javac compiles.
    java.exclude("**/platform/neoforge/**")
}

repositories {
    // ModMenu — an optional dependency, see ModMenuIntegration.
    maven("https://maven.terraformersmc.com/releases/") {
        name = "TerraformersMC"
    }
}

// Java toolchain, -Xlint:deprecation, JUnit/Mockito and publishing are the same on both
// loaders and live in one place.
apply(from = rootProject.file("gradle/common.gradle.kts"))

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")

    // Mojang's own names, so the shared src/ tree reads the same here as it does on the
    // NeoForge side (GuiGraphics, Component, ChatComponent — not the Yarn names the
    // Fabric API sources are written in; `modImplementation` remaps those for us).
    //
    // This adds nothing on 26+: those versions ship unobfuscated and have no mappings to
    // apply. Parchment is not layered on here as it is on NeoForge — it only renames
    // method parameters in the dev environment and has no effect on the built jar.
    the<dev.kikugie.loomx.LoomCompatDependencyExtension>().applyMojangMappings()

    "modImplementation"("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Compile-only: ModMenu supplies nothing at runtime, it only calls the mod's
    // `modmenu` entrypoint if the player happens to have it installed.
    "modCompileOnly"("com.terraformersmc:modmenu:$modMenuVersion")

    // Video playback shells out to external ffmpeg/yt-dlp binaries downloaded at
    // runtime (see MediaBinaries), so there are no extra compile dependencies.
}

// Expands the declared properties in fabric.mod.json. A missing property results in an
// error. Properties are expanded using ${} Groovy notation.
tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range_fabric" to minecraftVersionRangeFabric,
        "fabric_loader_version" to fabricLoaderVersion,
        "java_version" to javaVersion,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
        "api_version" to apiVersion,
    )
    inputs.properties(replaceProperties)

    // Both metadata files live in the shared src/main/resources; each loader expands
    // and ships only its own.
    exclude("META-INF/neoforge.mods.toml")

    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
}
