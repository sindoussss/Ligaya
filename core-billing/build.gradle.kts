// RevenueCat/Play Billing wrapper — entitlement state only; family/household ownership stays
// in core-backend (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 2 — ownership boundary).
//
// Step 44: real RevenueCat Android SDK dependency (com.revenuecat.purchases:purchases, verified
// against RevenueCat's own current docs — Purchases.configure/awaitCustomerInfo/awaitOfferings/
// awaitPurchase/awaitRestore all real, current API, not guessed). Compiles and is exercised by
// RevenueCatEntitlementRepositoryTest's fallback-path tests, but a real purchase/entitlement
// round trip needs a real RevenueCat project + API key + Google Play Console listing — see this
// module's own EntitlementRepository.kt doc comment and ACCOUNT_ACTIONS_NEEDED.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ligaya.core.billing"
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
    implementation(libs.revenuecat.purchases)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
