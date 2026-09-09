// Generic runtime-permission framework (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 19 — nearly
// every subsystem needs one). Added after the original Step 1 module scaffold; see the comment
// in settings.gradle.kts. No feature-specific permission logic lives here — that belongs to
// whichever module actually needs a permission (core-location, core-voice, ...), each of which
// depends on this module, never on each other.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ligaya.core.permissions"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
