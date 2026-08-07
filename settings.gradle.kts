pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The extractor fork's build wants a JDK this machine doesn't have; foojay lets Gradle
// provision it instead of failing on toolchain resolution.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// PipePipeExtractor (GPLv3, sibling checkout): built from source exactly like PipePipe's own
// client does -- jitpack has no working build of its current history. Substitutes the
// com.github.InfinityLoop1308.PipePipeExtractor:extractor dependency below.
includeBuild("../PipePipe/PipePipeExtractor")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // NewPipeExtractor is JitPack-only
    }
}

rootProject.name = "fyi-player"
include(":app")
