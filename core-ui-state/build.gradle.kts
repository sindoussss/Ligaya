// Pure Kotlin/JVM module — the presentation-mapping layer between the safety-critical engine
// and the UI. Depends on core-emergency-engine (to read its state types) but has no Android or
// Compose dependency itself, so mappings stay unit-testable without an emulator.
//
// This is the ONLY module :app is allowed to reach :core-emergency-engine's state through —
// enforced by the :checkModuleBoundaries task in the root build script.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // api, not implementation: this module's own public API (EmergencyController/SosResult,
    // Step 13) directly returns core-emergency-engine's EmergencyState — re-exporting exactly
    // the type its whole job is to expose, per this file's own header comment. A consumer
    // (Step 13: :app's SosScreenTest, constructing a fake SosResult) needs EmergencyState
    // resolvable to use this module's API at all. Same real compile error, same fix, as
    // core-data's identical situation with LigayaDatabase/RoomDatabase.
    //
    // Note on :checkModuleBoundaries: this does mean core-emergency-engine's classes become
    // transitively visible on :app's compile classpath (there is no way to expose EmergencyState
    // through this module's API without that). The check still catches what it was built to
    // catch — a direct Gradle dependency edge from :app straight to :core-emergency-engine,
    // i.e. :app constructing engine internals or adding its own logic against them — it was
    // never meant to make an EmergencyState value received back FROM this module's own sanctioned
    // interface untouchable; that value passing through is the design working as intended.
    api(project(":core-emergency-engine"))

    // Step 37: EmergencyController.observeSnapshot() returns a Flow<EmergencySnapshot?> — the
    // -core artifact only, deliberately not -android (which pulls in an android.os.Looper
    // reference for its Dispatchers.Main integration this pure-JVM module has no use for and, per
    // this module's own build.gradle.kts header, can't depend on anyway).
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
