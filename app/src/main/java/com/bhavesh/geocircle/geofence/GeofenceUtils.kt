package com.bhavesh.geocircle.geofence

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeofenceUtils {
    const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two coordinates in meters
     * using the Haversine formula.
     */
    fun distanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }
}

/**
 * Top-level convenience function for distance calculation using Haversine formula.
 */
fun distanceMeters(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double
): Double = GeofenceUtils.distanceMeters(lat1, lng1, lat2, lng2)
