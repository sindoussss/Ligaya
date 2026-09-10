// Pure Kotlin/JVM module — no Android dependency by design.
// Per LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 12, this owns the deterministic safety
// engine and emergency state machine (section 25). Keeping it Android-independent lets it be
// unit tested on a plain JVM, without an emulator or Robolectric.
//
// Step 46: jacoco applied here (and only here) to get a real, measured line/branch coverage
// report for the state machine — "coverage tool output reviewed against the diagram" is this
// step's own literal Tests/checks entry, not just a manual read-through of the test file.
plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
