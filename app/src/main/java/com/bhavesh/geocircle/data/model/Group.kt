package com.bhavesh.geocircle.data.model

data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    val geofenceRadius: String = "200 metres",
    val members: List<String> = emptyList(),
    val alerts: List<String> = emptyList()
)
