pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor is not published to Maven Central — that group carries snapshots only.
        // JitPack is the real channel, and it is needed for the TeamNewPipe nanojson fork anyway.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "music"
include(":app")
