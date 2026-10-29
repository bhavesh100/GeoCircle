package com.bhavesh.geocircle.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceUtilsTest {

    @Test
    fun distanceBetweenIdenticalPointsIsZero() {
        val lat = 37.4220
        val lng = -122.0841
        val distance = distanceMeters(lat, lng, lat, lng)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun distanceBetweenKnownLocationsIsAccurate() {
        // Distance between White House (38.8977, -77.0365) and Washington Monument (38.8895, -77.0353)
        // is approximately 915 meters.
        val distance = distanceMeters(38.8977, -77.0365, 38.8895, -77.0353)
        assertTrue(
            "Expected distance to be around 915m, was: $distance",
            distance in 900.0..930.0
        )
    }

    @Test
    fun distanceForOneDegreeLatitudeIsApprox111km() {
        // 1 degree latitude across equator is approximately 111.19 km
        val distance = distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(
            "Expected distance to be around 111.2 km, was: $distance",
            distance in 111000.0..111500.0
        )
    }

    @Test
    fun distanceSymmetryCheck() {
        val lat1 = 12.9716
        val lng1 = 77.5946
        val lat2 = 13.0827
        val lng2 = 80.2707

        val d1 = distanceMeters(lat1, lng1, lat2, lng2)
        val d2 = distanceMeters(lat2, lng2, lat1, lng1)

        assertEquals(d1, d2, 0.0001)
    }
}
