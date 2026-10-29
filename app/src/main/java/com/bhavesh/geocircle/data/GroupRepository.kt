package com.bhavesh.geocircle.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class GroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    val currentUserId: String?
        get() = auth.currentUser?.uid

    private val random = SecureRandom()
    private val codeCharacters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Readable uppercase alphanumeric

    private fun generateRandomCode(): String {
        return (1..6)
            .map { codeCharacters[random.nextInt(codeCharacters.length)] }
            .joinToString("")
    }

    private suspend fun generateUniqueCode(): String {
        repeat(5) {
            val candidate = generateRandomCode()
            val existing = firestore.collection("groups")
                .whereEqualTo("code", candidate)
                .limit(1)
                .get()
                .await()
            if (existing.isEmpty) {
                return candidate
            }
        }
        return generateRandomCode()
    }

    suspend fun getGroup(groupId: String): Group? {
        return try {
            val doc = firestore.collection("groups").document(groupId).get().await()
            doc.toObject(Group::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createGroup(
        name: String,
        lat: Double,
        lng: Double,
        radius: Double,
        userName: String
    ): Group {
        val uid = currentUserId ?: throw IllegalStateException("User not authenticated")
        val code = generateUniqueCode()

        val groupDocRef = firestore.collection("groups").document()
        val group = Group(
            id = groupDocRef.id,
            name = name.trim(),
            code = code,
            centerLat = lat,
            centerLng = lng,
            radiusMeters = radius,
            createdBy = uid,
            memberIds = listOf(uid)
        )

        groupDocRef.set(group).await()

        // Add creator as the initial member
        val creatorMember = Member(
            uid = uid,
            name = userName.trim().ifBlank { "Admin" },
            lat = lat,
            lng = lng,
            isInside = true,
            updatedAt = System.currentTimeMillis()
        )

        groupDocRef.collection("members")
            .document(uid)
            .set(creatorMember)
            .await()

        return group
    }

    suspend fun joinGroup(code: String, userName: String): Group? {
        val uid = currentUserId ?: throw IllegalStateException("User not authenticated")
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length != 6) return null

        val querySnapshot = firestore.collection("groups")
            .whereEqualTo("code", cleanCode)
            .limit(1)
            .get()
            .await()

        if (querySnapshot.isEmpty) return null

        val groupDoc = querySnapshot.documents.first()
        val group = groupDoc.toObject(Group::class.java)?.copy(id = groupDoc.id) ?: return null

        // Add user to memberIds array
        firestore.collection("groups")
            .document(group.id)
            .update("memberIds", FieldValue.arrayUnion(uid))
            .await()

        val newMember = Member(
            uid = uid,
            name = userName.trim().ifBlank { "Member" },
            lat = group.centerLat,
            lng = group.centerLng,
            isInside = true,
            updatedAt = System.currentTimeMillis()
        )

        firestore.collection("groups")
            .document(group.id)
            .collection("members")
            .document(uid)
            .set(newMember)
            .await()

        return group
    }

    fun observeUserGroups(uid: String): Flow<List<Group>> = callbackFlow {
        val listenerRegistration = firestore.collection("groups")
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val groups = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Group::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(groups)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val listenerRegistration = firestore.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val group = snapshot?.toObject(Group::class.java)?.copy(id = snapshot.id)
                trySend(group)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    fun observeMembers(groupId: String): Flow<List<Member>> = callbackFlow {
        val listenerRegistration = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val members = snapshot?.documents?.mapNotNull { doc ->
                    val member = doc.toObject(Member::class.java) ?: return@mapNotNull null
                    // Guarantee both 'isInside' and 'inside' are accurately read
                    val insideVal = doc.getBoolean("isInside") ?: doc.getBoolean("inside") ?: member.isInside
                    member.copy(isInside = insideVal)
                } ?: emptyList()

                trySend(members)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    suspend fun updateMemberStatus(
        groupId: String,
        lat: Double,
        lng: Double,
        isInside: Boolean
    ): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw IllegalStateException("User not authenticated")
        val updates = mapOf(
            "lat" to lat,
            "lng" to lng,
            "isInside" to isInside,
            "inside" to isInside,
            "updatedAt" to System.currentTimeMillis()
        )

        // Using set with merge to ensure it creates or updates safely
        firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(uid)
            .set(updates, SetOptions.merge())
            .await()
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> = runCatching {
        val uid = currentUserId ?: throw IllegalStateException("User not authenticated")
        
        // Remove from members subcollection
        firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(uid)
            .delete()
            .await()

        // Remove from group memberIds
        firestore.collection("groups")
            .document(groupId)
            .update("memberIds", FieldValue.arrayRemove(uid))
            .await()
    }
}
