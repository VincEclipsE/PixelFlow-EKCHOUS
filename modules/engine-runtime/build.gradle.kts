plugins {
    `java-library`
    application
}

description = "JOGL NEWT host: RenderTarget, resource loading, GLEventListener frame loop."

dependencies {
    api(project(":pixelflow-core"))

    testImplementation(libs.bundles.junit)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set(providers.gradleProperty("mainClass").orElse("studio.engine.Smoke"))
}
