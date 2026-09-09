// Backend/auth client: accounts, household/Safety Circle membership, emergency events,
// backend-enforced authorization (LIGAYA_ARCHITECTURE_FINAL_VOICE.md sections 24, 27).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ligaya.core.backend"
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)

    // core-backend reuses core-data's EmergencyProfile domain model/serializer so the same
    // profile shape is written to Firestore as is persisted locally (section 24: USER's
    // emergency_profile field). This is the first inter-module dependency between two core-*
    // modules — a deliberate choice, not an accident: duplicating the model would let the two
    // representations drift.
    implementation(project(":core-data"))

    // Step 31: AuditingEmergencyStateMachine wraps core-emergency-engine's EmergencyStateMachine
    // directly (not core-data's PersistedEmergencyStateMachine) so this module's own audit-log
    // writer is the only thing driving it — core-data's own persistence wrapper stays untouched
    // and, per its own doc comment, still deliberately knows nothing about core-backend.
    implementation(project(":core-emergency-engine"))

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
