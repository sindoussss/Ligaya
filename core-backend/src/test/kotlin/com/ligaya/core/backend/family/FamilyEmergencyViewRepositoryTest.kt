package com.ligaya.core.backend.family

import com.ligaya.core.data.family.FamilyEmergencyLocation
import com.ligaya.core.data.family.FamilyEmergencyView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers parseFamilyEmergencyView against the exact loosely-typed Map<String, Any?> shape a real
 * Firebase Functions callable result carries — including the two things most likely to break
 * silently: numeric types arriving as Int vs Long vs Double (JSON has one number type; the JVM
 * doesn't), and the location key being absent entirely (Step 20's whole point) vs present-but-null.
 */
class FamilyEmergencyViewRepositoryTest {

    @Test
    fun `parses a full view with location present`() {
        val data = mapOf(
            "incidentType" to "FIRE",
            "time" to 1_700_000_000_000L,
            "status" to "ACTIVE",
            "alertState" to "CONFIRMED",
            "location" to mapOf("latitude" to 14.5995, "longitude" to 120.9842),
        )

        val result = parseFamilyEmergencyView(data)

        assertEquals(
            FamilyEmergencyView(
                incidentType = "FIRE",
                timeEpochMillis = 1_700_000_000_000L,
                status = "ACTIVE",
                alertState = "CONFIRMED",
                location = FamilyEmergencyLocation(14.5995, 120.9842),
            ),
            result,
        )
    }

    @Test
    fun `parses a view with the location key entirely absent as null location`() {
        val data = mapOf(
            "incidentType" to "MEDICAL",
            "time" to 1_700_000_000_000L,
            "status" to "ACTIVE",
            "alertState" to null,
            // "location" key deliberately not present at all — the permission-denied case.
        )

        val result = parseFamilyEmergencyView(data)

        assertNull(result.location)
    }

    @Test
    fun `parses a view where location is present but explicitly null (permitted, no fix yet)`() {
        val data = mapOf(
            "incidentType" to "POLICE",
            "time" to 1_700_000_000_000L,
            "status" to "ACTIVE",
            "alertState" to "SENT",
            "location" to null,
        )

        val result = parseFamilyEmergencyView(data)

        assertNull(result.location)
    }

    @Test
    fun `numeric fields arriving as Int rather than Long still parse correctly`() {
        // Firebase's own callable result deserialization can hand back Int for values that fit,
        // not always Long/Double — Number-typed casts in the parser must tolerate either.
        val data = mapOf(
            "incidentType" to "RESCUE",
            "time" to 1_700_000_000,
            "status" to "ACTIVE",
            "alertState" to null,
            "location" to mapOf("latitude" to 14, "longitude" to 121),
        )

        val result = parseFamilyEmergencyView(data)

        assertEquals(1_700_000_000L, result.timeEpochMillis)
        assertEquals(FamilyEmergencyLocation(14.0, 121.0), result.location)
    }
}
