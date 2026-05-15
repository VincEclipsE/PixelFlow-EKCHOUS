plugins {
    base
}

allprojects {
    group = "studio"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven {
            name = "JogAmp"
            url = uri("https://jogamp.org/deployment/maven")
            content { includeGroup("org.jogamp.gluegen"); includeGroup("org.jogamp.jogl") }
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-Xlint:all,-serial,-processing",
            "-parameters",
            "-Xmaxerrs", "9999",
        ))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

    // Both src/main/java and src/main/resources currently hold .gitkeep markers
    // (we have no real sources until M1). The default sourcesJar fails on the
    // duplicate. Warn-not-fail until we delete the markers.
    tasks.withType<AbstractArchiveTask>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
