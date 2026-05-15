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
