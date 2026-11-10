plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.github.ben-manes.versions") version "0.51.0"
}

android {
    namespace = "com.ministermux"
    
    defaultConfig {
        applicationId = "com.ministermux"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE") ?: "ministermux.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: System.getenv("SIGNING_KEY_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "ministermux"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    flavorDimensions += "arch"
    productFlavors {
        create("universal") {
            dimension = "arch"
        }
        create("aarch64") {
            dimension = "arch"
            ndk { abiFilters += "arm64-v8a" }
        }
        create("arm") {
            dimension = "arch"
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("x86_64") {
            dimension = "arch"
            ndk { abiFilters += "x86_64" }
        }
        create("x86") {
            dimension = "arch"
            ndk { abiFilters += "x86" }
        }
    }

    // Assets: bootstrap + MCP servers + Skills
    sourceSets {
        main {
            assets.srcDirs(
                "src/main/assets",
                "../../bootstrap/output",
                "../../mcp-servers",
                "../../skills"
            )
        }
    }

    // NDK configuration
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version("3.22.1")
        }
    }

    // Packaging options for native libs
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
        doNotStrip "*/*/libtermux-exec.so"
        doNotStrip "*/*/libtermux-pty.so"
        pickFirst "**/libc++_shared.so"
    }
}

dependencies {
    val libs = libs

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat.appcompat)
    implementation(libs.androidx.activity.activity.ktx)
    implementation(libs.androidx.fragment.fragment.ktx)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.datastore.preferences)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.okhttp.okhttp)
    implementation(libs.okhttp.okio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websocket)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Local modules
    implementation(project(":api"))
    implementation(project(":boot"))
    implementation(project(":x11"))
    implementation(project(":tasker"))
    implementation(project(":widget"))
    implementation(project(":float"))
    implementation(project(":styling"))

    // Termux native libraries (prebuilt)
    implementation(files("src/main/jniLibs/${android.defaultConfig.ndk.abiFilters.single()}/libtermux-exec.so"))
    implementation(files("src/main/jniLibs/${android.defaultConfig.ndk.abiFilters.single()}/libtermux-pty.so"))
}