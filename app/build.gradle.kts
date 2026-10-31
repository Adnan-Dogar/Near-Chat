plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.neartalk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nearchat.preview"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-preview"
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
    testImplementation("junit:junit:4.13.2")
}
