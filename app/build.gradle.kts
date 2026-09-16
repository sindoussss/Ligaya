// Composition root + UI navigation shell.
//
// Architecture boundary (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 11 + implementation-plan
// UI/UX architecture): this module must never depend on :core-emergency-engine directly — it
// reaches emergency/domain state only through :core-ui-state's presentation-mapped models.
// Enforced by the root project's :checkModuleBoundaries task.
//
// Step 13: this module also depends on :core-data now, but only to construct the CONCRETE
// EmergencyController (DefaultEmergencyController) at the composition root — MainActivity does
// this once and hands the result to Composables typed as core-ui-state's EmergencyController
// interface. No Composable or navigation code in this module ever imports a core-data or
// core-emergency-engine type directly; only MainActivity's wiring code does.
//
// Resolving the Step 38/39 "no live EmergencyCompanionCoordinator" gap: this module now also
// depends on :core-ai/:core-voice/:core-permissions so MainActivity can assemble a real one
// (real on-device STT/TTS, real permission gating) — see MainActivity's own doc comment. The
// Gemini API key it needs is read here from local.properties' GEMINI_API_KEY entry (falling back
// to the GEMINI_API_KEY environment variable, matching core-ai's own live smoke test), never
// hardcoded, and local.properties is already gitignored. Absent either source, the key resolves
// to an empty string and MainActivity falls back to a safe canned reply instead of a live Gemini
// call — nothing here requires a key to exist in order to build or run.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

private fun localOrEnvProperty(properties: Properties, name: String): String =
    properties.getProperty(name) ?: System.getenv(name) ?: ""

val localProperties: Properties = Properties().also {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(it::load)
    }
}

val geminiApiKey: String = localOrEnvProperty(localProperties, "GEMINI_API_KEY")

// Step 48: same never-hardcoded, never-required-to-build pattern as geminiApiKey above — see
// ACCOUNT_ACTIONS_NEEDED.md for what setting this up actually requires (a Google Cloud project
// with Places API (New) enabled and billing on). Absent, MainActivity skips the emergency-service
// (Places) flow entirely rather than firing a request guaranteed to fail on an empty key.
val placesApiKey: String = localOrEnvProperty(localProperties, "PLACES_API_KEY")

// ACCOUNT_ACTIONS_NEEDED.md item 6: same never-hardcoded, never-required-to-build pattern as
// geminiApiKey/placesApiKey above. This is a Google Cloud OAuth 2.0 *Web* client ID (Credentials
// page — "Web application" type), not an Android client ID and not a Firebase API key; Credential
// Manager's GetGoogleIdOption needs it as the audience the returned ID token is issued for. Absent,
// the "Continue with Google" button says plainly that it is not set up rather than launching a
// picker guaranteed to fail.
val googleWebClientId: String = localOrEnvProperty(localProperties, "GOOGLE_WEB_CLIENT_ID")

android {
    namespace = "com.ligaya.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ligaya.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(project(":core-ui-state"))
    implementation(project(":core-data"))
    implementation(project(":design-system"))
    implementation(project(":feature-home"))
    implementation(project(":feature-emergency-active"))
    // Now also constructs the real EmergencyCompanionCoordinator (see the header comment above
    // and MainActivity), not just VoicePipelinePhase.
    implementation(project(":feature-companion"))
    // Visual design screen 2: the Onboarding route rendered a PlaceholderScreen until now —
    // feature-onboarding existed (Step 42) but was never wired into the app at all.
    implementation(project(":feature-onboarding"))
    // Visual design screen 3: AuthRepository, so the composition root can choose which
    // implementation backs account creation (see MainActivity).
    implementation(project(":core-backend"))
    // ACCOUNT_ACTIONS_NEEDED.md item 6: Credential Manager, the current (non-deprecated) way to get
    // a real Google ID token from the device's own Google account picker.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    // Real STT/TTS/Gemini/permission wiring for the coordinator MainActivity now assembles.
    implementation(project(":core-ai"))
    implementation(project(":core-voice"))
    implementation(project(":core-permissions"))
    // Step 48: MainActivity now orchestrates the real Location and Emergency-Service (Places)
    // flows too, automatically, once EMERGENCY_ACTIVE is reached — see MainActivity's own doc
    // comment and DefaultEmergencyController's reportLocationFlow/reportEmergencyServiceFlow.
    implementation(project(":core-location"))
    implementation(project(":core-places"))
    // Architecture sections 6, 7 and 8. These three modules were built and tested but never depended on
    // here, which is why their routes showed a generic placeholder: the Safety Circle tab (which replaced
    // a "Tools" tab the architecture never asks for) and Ligaya+ are reachable from the app now.
    implementation(project(":feature-safetycircle"))
    implementation(project(":feature-paywall"))
    implementation(project(":core-billing"))
    implementation(libs.play.services.location)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // Visual design screen 1: brand-themed system launch window (see res/values/themes.xml for
    // what it replaces — the stock blue-robot splash a cold start showed before this).
    implementation(libs.androidx.core.splashscreen)
    // lifecycleScope/repeatOnLifecycle — for the foreground-only wake-word listening loop and the
    // EMERGENCY_ACTIVE snapshot observer, both of which must start/stop with the Activity's own
    // lifecycle, not just onCreate/onDestroy.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // GrantPermissionRule — Step 48's MainActivity now requests RECORD_AUDIO for real in
    // onCreate; MainActivity-launching tests pre-grant it (ordered ahead of the compose rule) so
    // the real system permission dialog never appears and steals window focus, same class of bug
    // already found and fixed once for this exact reason after Step 39.
    androidTestImplementation(libs.androidx.test.rules)
    // UiDevice — Step 50's real-SOS POST_NOTIFICATIONS-denied test needs to interact with the
    // real system permission dialog (deny it), which lives outside this app's own Compose view
    // hierarchy and so isn't reachable through ComposeTestRule's own node queries.
    androidTestImplementation(libs.androidx.test.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
