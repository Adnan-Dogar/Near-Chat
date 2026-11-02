plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nearchat.selfhost"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nearchat.selfhost.v08"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0-selfhost-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
