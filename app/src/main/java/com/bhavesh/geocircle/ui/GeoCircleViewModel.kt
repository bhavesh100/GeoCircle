package com.bhavesh.geocircle.ui

import androidx.lifecycle.ViewModel
import com.bhavesh.geocircle.data.model.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class GeoCircleViewModel : ViewModel() {

    private val initialGroups = listOf(
        Group(
            id = "g1",
            name = "Weekend Crew",
            inviteCode = "GEO482",
            geofenceRadius = "200 metres",
            members = listOf("Bhavesh (You)", "Alex", "Sarah", "David"),
            alerts = listOf("Safe zone created", "Alex joined the group")
        ),
        Group(
            id = "g2",
            name = "Family Circle",
            inviteCode = "GEO912",
            geofenceRadius = "200 metres",
            members = listOf("Bhavesh (You)", "Mom", "Dad"),
            alerts = listOf("Family Circle setup created")
        )
    )

    private val _groups = MutableStateFlow<List<Group>>(initialGroups)
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    fun createGroup(name: String): Group {
        val code = "GEO" + (100..999).random()
        val newGroup = Group(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            inviteCode = code,
            geofenceRadius = "200 metres",
            members = listOf("You (Admin)"),
            alerts = listOf("Group created with invite code $code")
        )
        _groups.value = _groups.value + newGroup
        return newGroup
    }

    fun joinGroup(code: String): String? {
        val trimmedCode = code.trim().uppercase()
        if (trimmedCode.isBlank()) return null

        val existing = _groups.value.find { it.inviteCode.uppercase() == trimmedCode }
        if (existing != null) {
            return existing.id
        }

        val joinedGroup = Group(
            id = UUID.randomUUID().toString(),
            name = "Joined Circle ($trimmedCode)",
            inviteCode = trimmedCode,
            geofenceRadius = "200 metres",
            members = listOf("You", "Group Admin"),
            alerts = listOf("You joined using invite code $trimmedCode")
        )
        _groups.value = _groups.value + joinedGroup
        return joinedGroup.id
    }

    fun getGroupById(id: String): Group? {
        return _groups.value.find { it.id == id }
    }
}
