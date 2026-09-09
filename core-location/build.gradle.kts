// Owns the location flow: current GPS, last-known fallback, unavailable state
// (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 14).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ligaya.core.location"
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
    // Step 15 reports into the engine's LocationFlowState — a plain Kotlin/JVM type, so this
    // stays a compile-only edge with no Android dependency pulled in from core-emergency-engine.
    implementation(project(":core-emergency-engine"))

    // Permission checking is generic infrastructure (see core-permissions' own build script
    // comment: feature modules depend on it, never on each other).
    implementation(project(":core-permissions"))

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    // Task<T>.await() — bridges FusedLocationProviderClient's Task-based API into suspend
    // functions without a hand-rolled suspendCancellableCoroutine wrapper.
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
