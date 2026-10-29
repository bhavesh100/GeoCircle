package com.bhavesh.geocircle.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhavesh.geocircle.data.model.Group
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GeoCircleViewModel : ViewModel() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _uid = MutableStateFlow<String?>(null)
    val uid: StateFlow<String?> = _uid.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

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

    init {
        signInAnonymously()
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    _uid.value = currentUser.uid
                    Log.d("GeoCircleAuth", "Already authenticated with UID: ${currentUser.uid}")
                } else {
                    val authResult = auth.signInAnonymously().await()
                    val newUid = authResult.user?.uid
                    _uid.value = newUid
                    Log.d("GeoCircleAuth", "Signed in anonymously with UID: $newUid")
                }
            } catch (e: Exception) {
                Log.e("GeoCircleAuth", "Anonymous sign-in failed", e)
                _authError.value = e.localizedMessage ?: "Failed to sign in anonymously"
            }
        }
    }

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
