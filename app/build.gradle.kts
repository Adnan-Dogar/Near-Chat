plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.neartalk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.neartalk"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-online"
        // Firebase client config can be supplied by GitHub Actions secrets / local Gradle properties.
        fun config(name: String): String = (System.getenv(name)?.takeIf { it.isNotBlank() } ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() } ?: "SET_$name")
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${config("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${config("FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${config("FIREBASE_PROJECT_ID")}\"")
    }
    buildFeatures { buildConfig = true }
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
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    testImplementation("junit:junit:4.13.2")
}
