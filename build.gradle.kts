// Root build.gradle.kts - Common configuration for all modules
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

// Version catalog access
val libs = libs

allprojects {
    group = "com.ministermux"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

ext {
    // Single source of truth for version
    appVersionCode = 100
    appVersionName = "1.0.0"
    minSdk = 24
    targetSdk = 34
    compileSdk = 34
    namespace = "com.ministermux"
    ndkVersion = "26.1.10909125"
}

// Common Android configuration applied to all modules
subprojects {
    afterEvaluate { project ->
        if (project.plugins.hasPlugin("com.android.library") || project.plugins.hasPlugin("com.android.application")) {
            android {
                compileSdk = rootProject.ext.compileSdk
                ndkVersion = rootProject.ext.ndkVersion

                defaultConfig {
                    minSdk = rootProject.ext.minSdk
                    targetSdk = rootProject.ext.targetSdk
                    versionCode = rootProject.ext.appVersionCode
                    versionName = rootProject.ext.appVersionName
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                kotlinOptions {
                    jvmTarget = "17"
                    freeCompilerArgs += [
                        "-Xopt-in=kotlin.RequiresOptIn",
                        "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
                    ]
                }

                // Manifest placeholders for merging
                manifestPlaceholders = [
                    applicationId: rootProject.ext.namespace,
                    minSdk: rootProject.ext.minSdk,
                    targetSdk: rootProject.ext.targetSdk
                ]

                // Native libs packaging
                packagingOptions {
                    jniLibs {
                        useLegacyPackaging = true
                    }
                }
            }
        }
    }
}

// Clean task for all modules
tasks.register("cleanAll", Delete::class) {
    group = "build"
    description = "Clean all modules"
    delete(fileTree(rootDir) {
        include("**/build")
        include("**/.gradle")
    })
}