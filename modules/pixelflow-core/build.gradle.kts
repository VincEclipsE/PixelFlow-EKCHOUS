plugins {
    `java-library`
}

description = "Forked PixelFlow library, decoupled from Processing/PApplet. Pure JOGL."

dependencies {
    api(libs.bundles.jogl)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
