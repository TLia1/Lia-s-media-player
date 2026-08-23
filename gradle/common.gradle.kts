// Settings shared by both loader buildscripts.
//
// Applied with `apply(from = ...)` rather than being a precompiled script plugin: that
// would need a buildSrc module, and this is the only thing that would live in it. The
// price is that the Kotlin DSL's type-safe accessors are not generated here, so
// extensions are configured by type and configurations are named as strings.
//
// Anything that differs between NeoForge and Fabric — the modding plugin, the archive
// name, which platform package javac sees, which metadata file is expanded — belongs in
// the loader's own buildscript, not here.

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

// Per-version: Mojang shipped Java 21 for 1.21.1-1.21.11 and moved to Java 25 in 26.1.
// Gradle provisions whichever toolchain is missing through the foojay resolver applied
// in settings.gradle.kts.
extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(project.property("deps.java").toString().toInt())
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // There is no linter on this project; this is the closest thing to one.
    options.compilerArgs.add("-Xlint:deprecation")
}

dependencies {
    // Unit tests. Plain JUnit 5 against non-Minecraft logic — no game boot — so they are
    // identical on both loaders.
    "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.2")
    "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.10.2")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    // Careful: this Mockito cannot generate mocks under Java 25, which the 26.x targets
    // compile and test against — it fails at mock creation, not at compile time, so it
    // passes on the 1.21.x half of the matrix and fails on the other. Prefer a
    // hand-written test double (see ShowCommandTest), or bump this first.
    "testImplementation"("org.mockito:mockito-core:5.11.0")
    "testImplementation"("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

extensions.configure<PublishingExtension> {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = extensions.getByType<BasePluginExtension>().archivesName.get()
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(rootProject.layout.projectDirectory.dir("repo"))
        }
    }
}
