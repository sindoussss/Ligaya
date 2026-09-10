// The Emergency Companion conversational loop (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 19,
// implementation Step 26): STT -> Gemini -> Response Validator -> TTS, on repeat. Was "non-UI
// orchestration only" through Step 26 — Step 39 is deliberately the step that adds the real
// screen (transcript, voice indicator, text fallback) here, in the same module as the
// coordinator it renders, rather than splitting them the way feature-emergency-active is split
// from core-ui-state: EmergencyCompanionCoordinator is a concrete class local to this module, not
// an interface reached across a module boundary, so there's no boundary this UI needs to cross.
//
// Still not added to the root project's :checkModuleBoundaries forbidden set even though it now
// has a screen: this module's core-emergency-engine dependency exists for
// EmergencySnapshotProvider (Step 26's own response-validation need, unrelated to the new
// screen — the screen itself never touches EmergencySnapshot, only phase/transcript/runOneTurn*),
// so the check's actual concern ("a UI screen bypassing core-ui-state's presentation mapping to
// read engine state directly") doesn't apply here the way it does for :app/:feature-home/
// :feature-emergency-active.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ligaya.feature.companion"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core-emergency-engine"))
    implementation(project(":core-ai"))
    implementation(project(":core-voice"))
    implementation(project(":design-system"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Test-only: constructing a real VoiceCaptureCoordinator in tests needs a PermissionChecker
    // fake to implement — core-voice's own production code already depends on core-permissions,
    // this just needs the type visible from this module's tests too.
    testImplementation(project(":core-permissions"))

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":core-permissions"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
