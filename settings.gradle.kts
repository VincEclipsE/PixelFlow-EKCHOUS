rootProject.name = "pixelflow-studio"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "JogAmp"
            url = uri("https://jogamp.org/deployment/maven")
            content { includeGroup("org.jogamp.gluegen"); includeGroup("org.jogamp.jogl") }
        }
    }
    // gradle/libs.versions.toml is auto-discovered into a catalog named "libs".
}

include("pixelflow-core")
include("engine-runtime")
include("studio-app")

project(":pixelflow-core").projectDir = file("modules/pixelflow-core")
project(":engine-runtime").projectDir = file("modules/engine-runtime")
project(":studio-app").projectDir = file("modules/studio-app")
