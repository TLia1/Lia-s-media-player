plugins {
    id("dev.kikugie.stonecutter")
}

// Which version the shared src/ tree is currently switched to. Rewritten by the
// `Set active project to <version>` tasks — do not edit by hand.
stonecutter active "1.21.1" /* [SC] DO NOT EDIT */

// Build every target in one pass. Ordered so the per-version `build` tasks never
// run concurrently: they all write to the same shared src/ tree.
stonecutter tasks {
    order("build")
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds the mod for every registered Minecraft version."
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs the unit tests against every registered Minecraft version."
    dependsOn(stonecutter.versions.map { ":${it.project}:test" })
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

    val docsVersion = stonecutter.vcsVersion.project
    fun versioned(vararg path: String): String =
        stonecutter.properties.raw(docsVersion, *path).asPrimitive().toString()

    val capturedProps = mapOf(
        "mod_version" to project.property("mod.version").toString(),
        "mod_id" to project.property("mod.id").toString(),
        "mod_name" to project.property("mod.name").toString(),
        "mod_group_id" to project.property("mod.group").toString(),
        "mod_license" to project.property("mod.license").toString(),
        "neo_version" to versioned("deps", "neoforge"),
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
