plugins {
    id("dev.kikugie.stonecutter")
}

// Which target the shared src/ tree is currently switched to. Rewritten by the
// `Set active project to <mc>-<loader>` tasks — do not edit by hand.
stonecutter active "1.21.1-fabric" /* [SC] DO NOT EDIT */

// ---------------------------------------------------------------------------
// Preprocessor parameters — shared by every target
// ---------------------------------------------------------------------------
// These live in the controller rather than in a buildscript because both loaders need
// them: with one buildscript per loader there is no longer a single `build.gradle.kts`
// to put them in, and duplicating them would be two places to forget.
stonecutter parameters {
    // Makes `//? if fabric { ... }` / `//? if neoforge { ... }` work. The loader is the
    // suffix of the subproject name; the Minecraft version, which every `//? if <1.21.5`
    // guard evaluates against, is `current.version` and is unaffected by the rename.
    //
    // Whole files that belong to one loader are *not* guarded this way — they live in
    // platform/<loader>/ and each buildscript excludes the other's package from javac.
    // A guard is for a few lines inside a shared file.
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")

    // -----------------------------------------------------------------------
    // Token replacements
    // -----------------------------------------------------------------------
    // 1.21.11 moved to Mojang's official names. Two of those renames are pure
    // token substitutions with no semantics at all — ResourceLocation alone is 7
    // imports and ~30 usages across 9 files — so they are rewritten at generation
    // time instead of being guarded by hand.
    //
    // The boolean is the *direction*: true rewrites the left token into the right
    // one, false rewrites it back. The shared src/ tree therefore round-trips —
    // switching the active target to 1.21.11 rewrites it to the new names, and
    // switching back restores the old ones.
    //
    // Only add renames here that are unambiguous substrings project-wide. Anything
    // that changes a signature or a call shape belongs in a //? guard.
    replacements.string(eval(current.version, ">=1.21.11")) {
        // net.minecraft.resources.ResourceLocation -> .resources.Identifier
        replace("ResourceLocation", "Identifier")
        // net.minecraft.Util -> net.minecraft.util.Util (same class, new package)
        replace("net.minecraft.Util", "net.minecraft.util.Util")
    }

    // 26.1 renamed the GUI drawing API wholesale: the GUI is no longer drawn but
    // *extracted* into a render state, so GuiGraphics became GuiGraphicsExtractor
    // and its draw methods were renamed to match. The three below are exact
    // one-for-one renames with identical signatures, over 14 files and ~80 call
    // sites. Everything else 26.1 changed needed a real guard instead.
    //
    // The leading dot and open paren matter: they keep the *reverse* direction
    // honest. Replacing a bare "text" back into "drawString" would rewrite the
    // word wherever it appeared; ".text(" only ever appears as this call.
    replacements.string(eval(current.version, ">=26.1")) {
        replace(".drawString(", ".text(")
        replace(".drawCenteredString(", ".centeredText(")
    }

    // The type rename needs a regex rather than a plain substring, because
    // NeoForge kept ScreenEvent.Render.getGuiGraphics() under its old name while
    // changing what it returns. A blanket substring swap rewrites that call site
    // into getGuiGraphicsExtractor(), which does not exist. The lookbehind says
    // what is actually meant: rename the type, never the getter.
    replacements.regex(eval(current.version, ">=26.1")) {
        replace(
            "(?<!get)GuiGraphics", "GuiGraphicsExtractor",
            "GuiGraphicsExtractor", "GuiGraphics",
        )
    }
}

// Build every target in one pass. Ordered so the per-target `build` tasks never
// run concurrently: they all write to the same shared src/ tree.
stonecutter tasks {
    order("build")
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds the mod for every registered Minecraft version and mod loader."
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs the unit tests against every registered Minecraft version and mod loader."
    dependsOn(stonecutter.versions.map { ":${it.project}:test" })
}

// Per-loader aggregates, for when only one side is being worked on. `buildAll` spans
// two Java toolchains and two modding toolchains and takes a while; these do not.
listOf("neoforge", "fabric").forEach { loader ->
    val targets = stonecutter.versions.filter { it.project.endsWith("-$loader") }
    tasks.register("build${loader.replaceFirstChar(Char::uppercase)}") {
        group = "build"
        description = "Builds the mod for every Minecraft version on $loader."
        dependsOn(targets.map { ":${it.project}:build" })
    }
    tasks.register("test${loader.replaceFirstChar(Char::uppercase)}") {
        group = "verification"
        description = "Runs the unit tests against every Minecraft version on $loader."
        dependsOn(targets.map { ":${it.project}:test" })
    }
}

// ---------------------------------------------------------------------------
// Documentation sync — keeps markdown files in sync with the project properties
// ---------------------------------------------------------------------------
// Run: ./gradlew updateDocs
// Markers in markdown files look like: <!-- key -->value<!-- /key -->
// The task replaces the value between the markers with the matching property.
//
// This lives in the controller rather than in a version subproject on purpose:
// the docs describe one primary Minecraft version (the VCS version), and reading
// it from whichever subproject happened to be active would rewrite the README
// with a different version every time the active version changed.
tasks.register("updateDocs") {
    group = "documentation"
    description = "Updates version numbers and mod properties in markdown documentation files."

    // The VCS target names a loader too ("1.21.1-neoforge"); the docs are about the
    // Minecraft version, and that is also the key the property tables are keyed by.
    val docsVersion = stonecutter.vcsVersion.version
    fun versionedFor(version: String, vararg path: String): String =
        stonecutter.properties.raw(version, *path).asPrimitive().toString()
    fun versioned(vararg path: String): String = versionedFor(docsVersion, *path)

    // The supported-version table is generated rather than hand-written: with
    // seven Minecraft versions on two loaders a hand-maintained copy drifts the
    // moment one is added or a loader build is bumped, and the docs-check workflow
    // would not catch it because it only verifies that updateDocs changes nothing.
    // Generating it makes that check meaningful for the table too.
    //
    // One row per Minecraft version rather than per subproject: every version is
    // built for both loaders, so a version x loader grid would be fourteen rows of
    // which half repeat the Minecraft column.
    val supportedVersions = stonecutter.versions
        .map { it.version }
        .distinct()
        .joinToString(
            separator = "\n",
            prefix = "| Minecraft | NeoForge | Fabric API | Java |\n|---|---|---|---|\n",
        ) { v ->
            val primary = if (v == docsVersion) " *(primary)*" else ""
            "| `${versionedFor(v, "deps", "minecraft")}`$primary" +
                " | `${versionedFor(v, "deps", "neoforge")}`" +
                " | `${versionedFor(v, "deps", "fabric_api")}`" +
                " | ${versionedFor(v, "deps", "java")} |"
        }

    val capturedProps = mapOf(
        "supported_versions" to supportedVersions,
        "mod_version" to project.property("mod.version").toString(),
        "mod_id" to project.property("mod.id").toString(),
        "mod_name" to project.property("mod.name").toString(),
        "mod_group_id" to project.property("mod.group").toString(),
        "mod_license" to project.property("mod.license").toString(),
        "api_version" to project.property("mod.api_version").toString(),
        "neo_version" to versioned("deps", "neoforge"),
        "fabric_api_version" to versioned("deps", "fabric_api"),
        "fabric_loader_version" to project.property("deps.fabric_loader").toString(),
        "minecraft_version" to versioned("deps", "minecraft"),
    )
    val capturedDocFiles = listOf("README.md", "TECHNICAL-DETAILS.md", "FEATURES.md")
        .map { layout.projectDirectory.file(it).asFile }
    val log = logger

    doLast {
        capturedDocFiles.forEach { f ->
            if (!f.exists()) {
                log.warn("updateDocs: ${f.name} not found, skipping.")
                return@forEach
            }
            var content = f.readText()
            var changed = false

            capturedProps.forEach { (key, value) ->
                // Pattern: <!-- key -->old_value<!-- /key -->
                // Preserves any formatting (*, _, ~, `) and spaces immediately surrounding the value
                val pattern = Regex(
                    """(<!--\s*$key\s*-->\s*[*_~`]*\s*)(.*?)(\s*[*_~`]*\s*<!--\s*/$key\s*-->)""",
                    RegexOption.DOT_MATCHES_ALL,
                )
                val newContent = pattern.replace(content) {
                    it.groupValues[1] + value + it.groupValues[3]
                }
                if (newContent != content) {
                    changed = true
                    content = newContent
                }
            }

            if (changed) {
                f.writeText(content)
                log.lifecycle("updateDocs: updated ${f.name}")
            } else {
                log.lifecycle("updateDocs: ${f.name} is already up to date")
            }
        }
    }
}

if (System.getProperty("idea.sync.active").toBoolean()) {
    val buildRootDir = rootProject.projectDir

    gradle.projectsEvaluated {
        val startParameter = gradle.startParameter
        startParameter.setTaskRequests(startParameter.taskRequests.map { request ->
            if (request.rootDir == null || request.rootDir == buildRootDir) request
            else object : org.gradle.TaskExecutionRequest {
                override fun getArgs(): List<String> = request.args
                override fun getProjectPath(): String? = null
                override fun getRootDir(): File? = null
            }
        })
    }
}
