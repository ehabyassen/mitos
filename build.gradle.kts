import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.net.URI

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("mitos.group").get()
version = providers.gradleProperty("mitos.version").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("intellij.platform.type").get(),
            providers.gradleProperty("intellij.platform.version").get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("JavaScript")
        bundledPlugin("com.intellij.javaee")
        bundledPlugin("com.intellij.javaee.web")
        bundledPlugin("com.intellij.spring")
        bundledPlugin("com.intellij.spring.mvc")

        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.vodafone.mitos"
        name = "Mitos"
        version = providers.gradleProperty("mitos.version")
        ideaVersion {
            sinceBuild = providers.gradleProperty("plugin.since.build")
            untilBuild = providers.gradleProperty("plugin.until.build")
        }
        description = """
            Mitos — Ariadne's thread through the Labyrinth.
            A bidirectional cross-language call-graph visualizer for Java, JSP and JavaScript.
            Place the caret on a method, JSP page, or JS function and press Ctrl+Alt+Shift+G.
        """.trimIndent()
        changeNotes = "Initial release."
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// ---------------------------------------------------------------------------
//  Vendored web assets
// ---------------------------------------------------------------------------
//  We must not reach the network at runtime (NFR-12, NFR-13). The graph is
//  rendered with Cytoscape.js inside JBCefBrowser, and its source is bundled
//  in the plugin JAR. We download it at *build* time, from cdnjs/unpkg, into
//  build/web-deps and copy it into src/main/resources/web/lib via
//  processResources. Versions are pinned in gradle.properties.

val webDepsDir = layout.buildDirectory.dir("web-deps")

data class WebDep(val url: String, val target: String)

val webDeps = listOf(
    WebDep(
        "https://unpkg.com/cytoscape@${providers.gradleProperty("cytoscape.version").get()}/dist/cytoscape.min.js",
        "cytoscape.min.js",
    ),
    WebDep(
        "https://unpkg.com/dagre@${providers.gradleProperty("dagre.version").get()}/dist/dagre.min.js",
        "dagre.min.js",
    ),
    WebDep(
        "https://unpkg.com/cytoscape-dagre@${providers.gradleProperty("cytoscape.dagre.version").get()}/cytoscape-dagre.js",
        "cytoscape-dagre.js",
    ),
    WebDep(
        "https://unpkg.com/cytoscape-cose-bilkent@${providers.gradleProperty("cytoscape.cose.bilkent.version").get()}/cytoscape-cose-bilkent.js",
        "cytoscape-cose-bilkent.js",
    ),
    WebDep(
        "https://unpkg.com/@popperjs/core@${providers.gradleProperty("popper.core.version").get()}/dist/umd/popper.min.js",
        "popper.min.js",
    ),
    WebDep(
        "https://unpkg.com/cytoscape-popper@${providers.gradleProperty("cytoscape.popper.version").get()}/cytoscape-popper.js",
        "cytoscape-popper.js",
    ),
    WebDep(
        "https://unpkg.com/cytoscape-svg@${providers.gradleProperty("cytoscape.svg.version").get()}/cytoscape-svg.js",
        "cytoscape-svg.js",
    ),
)

val downloadWebDeps by tasks.registering {
    description = "Downloads pinned Cytoscape.js + dependencies into build/web-deps."
    group = "build"
    val outDir = webDepsDir.get().asFile
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        webDeps.forEach { dep ->
            val target = outDir.resolve(dep.target)
            if (!target.exists() || target.length() == 0L) {
                logger.lifecycle("Mitos: downloading ${dep.target} from ${dep.url}")
                URI(dep.url).toURL().openStream().use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(downloadWebDeps)
    from(webDepsDir) {
        into("web/lib")
    }
}

tasks.test {
    useJUnitPlatform()
}
