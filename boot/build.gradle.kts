plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.termux.boot"
    defaultConfig { minSdk = 24; targetSdk = 34 }
}

dependencies {
    implementation(project(":core"))
}