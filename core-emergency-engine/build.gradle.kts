// Pure Kotlin/JVM module — no Android dependency by design.
// Per LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 12, this owns the deterministic safety
// engine and emergency state machine (section 25). Keeping it Android-independent lets it be
// unit tested on a plain JVM, without an emulator or Robolectric.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
