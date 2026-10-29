package com.bhavesh.geocircle.data

data class Group(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val radiusMeters: Double = 200.0,
    val createdBy: String = ""
)

data class Member(
    val uid: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val isInside: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
