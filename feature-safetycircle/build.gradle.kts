// The Safety Circle management screen (Step 43, LIGAYA_ARCHITECTURE_FINAL_VOICE.md §6). Depends
// on core-backend (Step 8's SafetyCircleRepository, extended this step with getMembers()) and
// core-data (HouseholdEntity/FamilyMemberEntity — both already fully built and tested; this
// module only presents them) plus design-system (Steps 33/34). No core-ui-state, no
// core-emergency-engine: household membership is entirely outside the emergency state machine.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ligaya.feature.safetycircle"
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
    implementation(project(":core-backend"))
    implementation(project(":core-data"))
    implementation(project(":design-system"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test-only: SafetyCircleScreenTest drives a real FirestoreSafetyCircleRepository against the
    // local Firebase emulator (Step 43's own "live/emulated backend" acceptance criterion) — same
    // Firebase SDK core-backend itself depends on (`implementation`, not `api`, there, so it isn't
    // transitively visible here without redeclaring it).
    androidTestImplementation(platform(libs.firebase.bom))
    androidTestImplementation(libs.firebase.auth.ktx)
    androidTestImplementation(libs.firebase.firestore.ktx)
    // FirestoreSafetyCircleRepository's constructor takes a FirebaseFunctions (for invite-by-email),
    // so the type has to resolve here even though these tests pass null for it.
    androidTestImplementation(libs.firebase.functions.ktx)
    androidTestImplementation(libs.kotlinx.coroutines.play.services)
}
