plugins {
    `java-library`
    application
    alias(libs.plugins.beryx.runtime)
}

description = "DAG runtime + Swing/FlatLaf UI shell with imgui-node-editor canvas."

dependencies {
    api(project(":engine-runtime"))

    implementation(libs.bundles.flatlaf)
    implementation(libs.miglayout.swing)
    implementation(libs.bundles.dockingframes)
    implementation(libs.bundles.imgui)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set(providers.gradleProperty("mainClass").orElse("studio.ui.StudioApp"))
}

// Stage starter .pflow projects and bundled sample .pftool tools into the
// resources output so the packaged app can resolve them via the classpath
// fallback in MainFrame.
val stageBundledResources by tasks.registering(Copy::class) {
    from(rootProject.projectDir.resolve("starters")) {
        include("*.pflow")
        into("starters")
    }
    from(rootProject.projectDir.resolve("tools")) {
        include("sample-*.pftool")
        into("tools")
    }
    into(layout.buildDirectory.dir("generated/bundled-resources"))
}
sourceSets["main"].resources.srcDir(stageBundledResources.map { it.destinationDir })

// ---------------------------------------------------------------------------
// M4 — jlink runtime + jpackage Windows installer.
//
// Run:
//   ./gradlew :studio-app:runtime    # jlink'd JRE under build/runtime/
//   ./gradlew :studio-app:jpackage   # native Windows installer under build/jpackage/
// ---------------------------------------------------------------------------
runtime {
    options.set(listOf(
        "--strip-debug",
        "--compress", "2",
        "--no-header-files",
        "--no-man-pages",
    ))
    // App isn't modular; the runtime needs every JDK module the deps reach.
    // Detected via `jdeps --print-module-deps`; this list covers JOGL + Swing +
    // FlatLaf + Jackson + ByteBuffer/Unsafe usage.
    modules.set(listOf(
        "java.base",
        "java.desktop",
        "java.logging",
        "java.management",
        "java.prefs",
        "java.naming",
        "java.sql",          // Jackson databind pulls this in
        "java.xml",          // FlatLaf reads XML resource configs
        "java.datatransfer",
        "jdk.unsupported",   // sun.misc.Unsafe — JOGL needs it
        "java.scripting",    // Jackson's JsonPath occasionally; cheap to include
    ))

    jpackage {
        // Default to "exe" so the full ./gradlew :studio-app:jpackage produces
        // a Windows installer when WiX is present on the build machine.
        // Override at the CLI: ./gradlew :studio-app:jpackage -PinstallerType=msi
        installerType = providers.gradleProperty("installerType").getOrElse("exe")
        imageName     = "PixelFlowStudio"
        installerName = "PixelFlowStudio"
        appVersion    = "0.1.0"
        installerOptions = listOf(
            "--vendor", "vinceclipse",
            "--description", "PixelFlow Studio — composable GPU image-processing pipelines",
            "--copyright", "MIT-licensed, derived from PixelFlow by Thomas Diewald",
            "--win-dir-chooser",
            "--win-shortcut",
            "--win-menu",
            "--win-menu-group", "PixelFlow Studio",
        )
        jvmArgs = listOf(
            "-Xms128m", "-Xmx1g",
        )
    }
}

// Zip the portable app-image so the studio can ship as a no-installer
// download. Run with: ./gradlew :studio-app:packagePortableZip
val packagePortableZip by tasks.registering(Zip::class) {
    dependsOn("jpackageImage")
    archiveBaseName.set("PixelFlowStudio")
    archiveVersion.set(rootProject.version.toString())
    archiveClassifier.set("portable-windows")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    from(layout.buildDirectory.dir("jpackage/PixelFlowStudio"))
    into("PixelFlowStudio")
}

tasks.named<JavaExec>("run") {
    // Forward -Pproject / -Pframes / -Pout to the JVM as system properties so
    // headless tools (studio.headless.HeadlessSmoke) can read them.
    providers.gradleProperty("project").orNull?.let { systemProperty("project", it) }
    providers.gradleProperty("frames" ).orNull?.let { systemProperty("frames",  it) }
    providers.gradleProperty("out"    ).orNull?.let { systemProperty("out",     it) }
    // Run from the repo root so default relative paths (e.g. "starters/...")
    // resolve to the project tree rather than build/install.
    workingDir = rootProject.projectDir
}
