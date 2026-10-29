package com.bhavesh.geocircle.geofence

import com.bhavesh.geocircle.data.Group
import com.bhavesh.geocircle.data.GroupRepository

class GeofenceMonitor(
    var group: Group,
    private val repository: GroupRepository
) {
    var lastIsInside: Boolean? = null
        private set

    var lastUpdateTime: Long = 0L
        private set

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    /**
     * Called whenever a new location is recorded.
     * Computes whether the coordinates are inside the group's geofence radius.
     * Calls repository.updateMemberStatus only when the inside/outside state
     * changes OR every 30 seconds as a heartbeat.
     *
     * @return true if an update was dispatched to the repository, false otherwise.
     */
    suspend fun onLocation(lat: Double, lng: Double): Boolean {
        val distance = distanceMeters(lat, lng, group.centerLat, group.centerLng)
        val isInside = distance <= group.radiusMeters
        val now = System.currentTimeMillis()

        val stateChanged = lastIsInside == null || lastIsInside != isInside
        val heartbeatDue = (now - lastUpdateTime) >= HEARTBEAT_INTERVAL_MS

        if (stateChanged || heartbeatDue) {
            repository.updateMemberStatus(
                groupId = group.id,
                lat = lat,
                lng = lng,
                isInside = isInside
            )
            lastIsInside = isInside
            lastUpdateTime = now
            return true
        }

        return false
    }
}
