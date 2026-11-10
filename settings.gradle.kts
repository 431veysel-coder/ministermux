pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "8.4.0" apply false
        id("com.android.library") version "8.4.0" apply false
        id("org.jetbrains.kotlin.android") version "1.9.20" apply false
        id("com.github.ben-manes.versions") version "0.51.0" apply false
    }
}

rootProject.name = "MinisTermux"

include(":core")
include(":api")
include(":boot")
include(":x11")
include(":tasker")
include(":widget")
include(":float")
include(":styling")

// Version catalog for dependency management
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from("androidx") {
                artifact("androidx.core:core-ktx", "1.12.0")
                artifact("androidx.appcompat:appcompat", "1.6.1")
                artifact("androidx.activity:activity-ktx", "1.8.2")
                artifact("androidx.fragment:fragment-ktx", "1.6.2")
                artifact("androidx.lifecycle:lifecycle-runtime-ktx", "2.7.0")
                artifact("androidx.lifecycle:lifecycle-viewmodel-ktx", "2.7.0")
                artifact("androidx.datastore:datastore-preferences", "1.0.0")
            }
            from("kotlinx") {
                artifact("kotlinx-coroutines-android", "1.7.3")
                artifact("kotlinx-serialization-json", "1.6.0")
            }
            from("okhttp") {
                artifact("okhttp", "4.12.0")
                artifact("okio", "3.5.0")
            }
            from("ktor") {
                artifact("ktor-client-core", "2.3.8")
                artifact("ktor-client-android", "2.3.8")
                artifact("ktor-client-okhttp", "2.3.8")
                artifact("ktor-client-websocket", "2.3.8")
                artifact("ktor-serialization-kotlinx-json", "2.3.8")
            }
        }
    }
}