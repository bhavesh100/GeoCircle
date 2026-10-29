package com.bhavesh.geocircle.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhavesh.geocircle.data.Group
import com.bhavesh.geocircle.data.GroupRepository
import com.bhavesh.geocircle.data.Member
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GeoCircleViewModel(
    val repository: GroupRepository = GroupRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uid = MutableStateFlow<String?>(null)
    val uid: StateFlow<String?> = _uid.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _activeGroup = MutableStateFlow<Group?>(null)
    val activeGroup: StateFlow<Group?> = _activeGroup.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var userGroupsJob: Job? = null
    private var activeGroupJob: Job? = null
    private var membersJob: Job? = null

    init {
        signInAnonymously()
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = auth.currentUser
                val resolvedUid = if (currentUser != null) {
                    currentUser.uid
                } else {
                    val authResult = auth.signInAnonymously().await()
                    authResult.user?.uid
                }
                _uid.value = resolvedUid
                if (!resolvedUid.isNullOrBlank()) {
                    observeUserGroups(resolvedUid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous sign-in failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to sign in anonymously"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun observeUserGroups(uid: String) {
        userGroupsJob?.cancel()
        userGroupsJob = viewModelScope.launch {
            repository.observeUserGroups(uid)
                .catch { e ->
                    Log.e(TAG, "Error observing user groups", e)
                    _errorMessage.value = "Failed to load groups: ${e.localizedMessage}"
                }
                .collect { groupList ->
                    _groups.value = groupList
                }
        }
    }

    fun loadGroupDetails(groupId: String) {
        activeGroupJob?.cancel()
        membersJob?.cancel()

        activeGroupJob = viewModelScope.launch {
            repository.observeGroup(groupId)
                .catch { e ->
                    Log.e(TAG, "Error observing group $groupId", e)
                }
                .collect { group ->
                    _activeGroup.value = group
                }
        }

        membersJob = viewModelScope.launch {
            repository.observeMembers(groupId)
                .catch { e ->
                    Log.e(TAG, "Error observing members of group $groupId", e)
                }
                .collect { memberList ->
                    _members.value = memberList
                }
        }
    }

    fun createGroup(
        name: String,
        lat: Double,
        lng: Double,
        radius: Double,
        userName: String,
        onSuccess: (Group) -> Unit
    ) {
        if (name.isBlank()) {
            _errorMessage.value = "Group name cannot be empty"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val group = repository.createGroup(
                    name = name.trim(),
                    lat = lat,
                    lng = lng,
                    radius = radius,
                    userName = userName.trim().ifBlank { "Admin" }
                )
                onSuccess(group)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create group", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to create group"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun joinGroup(
        code: String,
        userName: String,
        onSuccess: (Group) -> Unit
    ) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length != 6) {
            _errorMessage.value = "Invite code must be exactly 6 characters"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val group = repository.joinGroup(
                    code = cleanCode,
                    userName = userName.trim().ifBlank { "Member" }
                )
                if (group != null) {
                    onSuccess(group)
                } else {
                    _errorMessage.value = "No group found with code $cleanCode"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join group", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to join group"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun leaveGroup(groupId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.leaveGroup(groupId)
                if (result.isSuccess) {
                    _activeGroup.value = null
                    _members.value = emptyList()
                    onSuccess()
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to leave group"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to leave group"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun simulateMemberStatus(groupId: String, isInside: Boolean) {
        viewModelScope.launch {
            try {
                val group = _activeGroup.value ?: repository.getGroup(groupId)
                val baseLat = group?.centerLat?.takeIf { it != 0.0 } ?: 19.0760
                val baseLng = group?.centerLng?.takeIf { it != 0.0 } ?: 72.8777
                val lat = if (isInside) baseLat else baseLat + 0.015
                val lng = if (isInside) baseLng else baseLng + 0.015

                Log.d(TAG, "Simulating status: isInside=$isInside, lat=$lat, lng=$lng for groupId=$groupId")
                val result = repository.updateMemberStatus(groupId, lat, lng, isInside)
                if (result.isFailure) {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    Log.e(TAG, "simulateMemberStatus failed: $err", result.exceptionOrNull())
                    _errorMessage.value = "Simulation failed: $err"
                } else {
                    Log.i(TAG, "simulateMemberStatus successful: isInside=$isInside")
                }
            } catch (e: Exception) {
                Log.e(TAG, "simulateMemberStatus exception", e)
                _errorMessage.value = "Failed to simulate status: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "GeoCircleViewModel"
    }
}
