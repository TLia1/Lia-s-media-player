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
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

// Per-version: Mojang shipped Java 21 for 1.21.1-1.21.11 and moved to Java 25 in 26.1.
// Gradle provisions whichever toolchain is missing through the foojay resolver applied
// in settings.gradle.kts.
extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(project.property("deps.java").toString().toInt())
    )
}

// ---------------------------------------------------------------------------
// Source-level quality checks
// ---------------------------------------------------------------------------
// Checkstyle, JaCoCo and -Werror run on **one** target, named by `quality.target` in
// gradle.properties. All fourteen compile the same rewritten src/ tree, so running them
// everywhere would be the same report fourteen times — and it would make a Minecraft
// deprecation on a version nobody has ported yet fail the whole matrix.
val qualityTarget = project.name == project.property("quality.target").toString()

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Compiler warnings the codebase is currently clean of, so a new one means a new
    // problem rather than one more line of noise nobody reads.
    options.compilerArgs.add("-Xlint:deprecation,unchecked,rawtypes,fallthrough")
    if (qualityTarget) {
        // Teeth, on the one target where a warning is definitely about this code and
        // not about the Minecraft version it happens to be compiled against.
        options.compilerArgs.add("-Werror")
    }
}

if (qualityTarget) {
    apply(plugin = "checkstyle")
    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.21.2"
        configFile = rootProject.layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
        // The ruleset is short and every rule in it is meant to hold, so a finding is a
        // failure rather than a number in a report nobody opens.
        maxWarnings = 0
        isIgnoreFailures = false
    }
    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    apply(plugin = "jacoco")
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }
    tasks.named<Test>("test") {
        finalizedBy("jacocoTestReport")
    }
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        // Deliberately no coverage *threshold*. Most of this codebase is rendering and
        // process plumbing that no unit test can reach, so a bar would either be set so
        // low it says nothing or would push people to test the wrong things. The number
        // is published so the trend is visible; that is the whole intent.
    }
}

dependencies {
    // Unit tests. Plain JUnit 5 against non-Minecraft logic — no game boot — so they are
    // identical on both loaders.
    "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.2")
    "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.10.2")
    "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    // Kept current on purpose: mock generation is a runtime concern, so a Mockito whose
    // Byte Buddy predates the JDK it runs on fails at mock creation, not at compile time —
    // it would pass on the 1.21.x half of the matrix (Java 21) and fail on the 26.x half
    // (Java 25). Bump this whenever a target moves to a newer JDK. A hand-written test
    // double is still preferred where one is easy to write (see ShowCommandTest).
    "testImplementation"("org.mockito:mockito-core:5.23.0")
    "testImplementation"("org.mockito:mockito-junit-jupiter:5.23.0")
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
