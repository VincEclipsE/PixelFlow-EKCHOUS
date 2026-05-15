plugins {
    `java-library`
    application
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
    mainClass.set(providers.gradleProperty("mainClass").orElse("studio.Main"))
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
