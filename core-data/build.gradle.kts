// Local persistence layer mirroring the backend data model (LIGAYA_ARCHITECTURE_FINAL_VOICE.md
// section 24) — Room entities/DAOs and the local EmergencyStateSnapshot used for crash recovery.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ligaya.core.data"
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Step 10: core-data needs core-emergency-engine's EmergencySnapshot/EmergencyState types
    // to serialize/restore them — the second core-* -> core-* dependency after
    // core-backend -> core-data (Step 5), same reasoning: reuse the one domain shape rather
    // than duplicate it. This is a Kotlin-JVM -> Kotlin-JVM edge and does not touch
    // :checkModuleBoundaries, which only restricts :app.
    implementation(project(":core-emergency-engine"))

    // Step 13: core-data implements core-ui-state's EmergencyController — it's the module with
    // the Android/Room/Service access that pure-Kotlin core-ui-state deliberately lacks. No
    // cycle: core-ui-state never depends back on core-data.
    implementation(project(":core-ui-state"))

    // Step 41's fix: DefaultEmergencyController.retryCall() wires a real Unified911FlowCoordinator
    // to the persisted machine (see that method's own doc comment on why this specific core-*
    // dependency doesn't violate this class's "touches nothing else" principle).
    implementation(project(":core-telephony"))

    // Step 11: EmergencyForegroundService needs NotificationCompat + ServiceCompat.startForeground
    // (the typed-FGS-aware overload that degrades correctly on pre-Android-14 too).
    implementation(libs.androidx.core.ktx)

    // api, not implementation: LigayaDatabase extends RoomDatabase, so it's part of this
    // module's own public API surface, not an internal implementation detail — a consumer
    // (Step 13: :app, calling LigayaDatabase.getInstance()) needs Room's types resolvable on
    // its own classpath to type-check any reference to LigayaDatabase at all, even just to call
    // one method on it. Caught as a real compile error, not designed in ahead of time.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Step 23: test-only, proving DefaultEmergencyController.submitVoiceIntent is reachable
    // through core-voice's real VoiceActivationCoordinator, not just callable in isolation.
    // core-data's own production code still never depends on core-voice or core-ai (see
    // DefaultEmergencyController's doc comment) — only this test does, and only androidTest.
    androidTestImplementation(project(":core-voice"))
    androidTestImplementation(project(":core-ai"))
    androidTestImplementation(project(":core-permissions"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
