package com.ligaya.core.places

import com.ligaya.core.emergencyengine.IncidentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers this step's acceptance criteria: correct category per incident type, and the
 *  Issue I fallback (no category) for RESCUE/OTHER. */
class IncidentTypeToPlacesCategoryMapperTest {

    @Test
    fun `FIRE maps to fire station`() {
        assertEquals(PlacesCategory.FIRE_STATION, IncidentTypeToPlacesCategoryMapper.categoryFor(IncidentType.FIRE))
    }

    @Test
    fun `POLICE maps to police`() {
        assertEquals(PlacesCategory.POLICE, IncidentTypeToPlacesCategoryMapper.categoryFor(IncidentType.POLICE))
    }

    @Test
    fun `MEDICAL maps to hospital`() {
        assertEquals(PlacesCategory.HOSPITAL, IncidentTypeToPlacesCategoryMapper.categoryFor(IncidentType.MEDICAL))
    }

    @Test
    fun `RESCUE has no category -- Issue I fallback`() {
        assertNull(IncidentTypeToPlacesCategoryMapper.categoryFor(IncidentType.RESCUE))
    }

    @Test
    fun `OTHER has no category -- Issue I fallback`() {
        assertNull(IncidentTypeToPlacesCategoryMapper.categoryFor(IncidentType.OTHER))
    }
}
