package com.loresuelvo.serviceprovider.ui.screens.profile

import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageZoneMapTest {
    private val zones = listOf(
        CoverageZone(6, "Comuna 6", "place-6"),
        CoverageZone(14, "Comuna 14", "place-14"),
    )

    @Test
    fun `maps only catalog place ids and selected regions`() {
        assertEquals(14, coverageZoneIdForPlaceId(zones, "place-14"))
        assertEquals(null, coverageZoneIdForPlaceId(zones, "unrelated"))
        assertEquals(setOf("place-6"), selectedCoveragePlaceIds(zones, listOf(6)))
        assertEquals(false, isCoverageMapConfigured(""))
        assertEquals(true, isCoverageMapConfigured("android-map-id"))
    }
}
