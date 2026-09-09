package com.ligaya.core.backend.household

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ligaya.core.data.entity.FamilyMemberEntity
import com.ligaya.core.data.entity.FamilyMemberStatus
import com.ligaya.core.data.entity.HouseholdEntity
import kotlinx.coroutines.tasks.await

/**
 * Household/Safety Circle membership (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 6), backed by
 * Firestore. The invite/accept/remove flow is governed entirely by backend/firestore.rules'
 * Step 8 rules: only the household owner can create (invite) a member record; the owner or the
 * member themselves can update (accept) or delete (remove/leave) it; a non-member can read
 * neither the household document nor any member record. This class performs no authorization
 * logic of its own — an unauthorized call simply fails when Firestore rejects it, per section
 * 27's "client UI is never trusted as the sole authorization mechanism."
 */
interface SafetyCircleRepository {
    suspend fun createHousehold(ownerId: String): String
    suspend fun getHousehold(householdId: String): HouseholdEntity?
    suspend fun inviteMember(householdId: String, memberUserId: String, relationship: String)
    suspend fun acceptInvite(householdId: String, memberUserId: String)
    suspend fun removeMember(householdId: String, memberUserId: String)
    suspend fun getMember(householdId: String, memberUserId: String): FamilyMemberEntity?
}

class FirestoreSafetyCircleRepository(
    private val firestore: FirebaseFirestore,
) : SafetyCircleRepository {

    private fun householdDoc(householdId: String) = firestore.collection("households").document(householdId)
    private fun memberDoc(householdId: String, memberUserId: String) =
        householdDoc(householdId).collection("members").document(memberUserId)

    override suspend fun createHousehold(ownerId: String): String {
        val ref = firestore.collection("households").document()
        ref.set(mapOf("owner_id" to ownerId, "subscription_state" to emptyMap<String, Any>())).await()
        return ref.id
    }

    override suspend fun getHousehold(householdId: String): HouseholdEntity? {
        val snapshot = householdDoc(householdId).get().await()
        val ownerId = snapshot.getString("owner_id") ?: return null
        // subscription_state isn't round-tripped field-for-field here — this step only needs
        // ownership, which is all the invite/accept/remove flow and its rules depend on.
        return HouseholdEntity(id = householdId, ownerId = ownerId, subscriptionStateJson = "{}")
    }

    override suspend fun inviteMember(householdId: String, memberUserId: String, relationship: String) {
        memberDoc(householdId, memberUserId).set(
            mapOf(
                "relationship" to relationship,
                "permissions" to emptyMap<String, Any>(),
                "notification_channel" to "push",
                "status" to FamilyMemberStatus.PENDING,
            ),
        ).await()
    }

    override suspend fun acceptInvite(householdId: String, memberUserId: String) {
        memberDoc(householdId, memberUserId)
            .set(mapOf("status" to FamilyMemberStatus.ACTIVE), SetOptions.merge())
            .await()
    }

    override suspend fun removeMember(householdId: String, memberUserId: String) {
        memberDoc(householdId, memberUserId).delete().await()
    }

    override suspend fun getMember(householdId: String, memberUserId: String): FamilyMemberEntity? {
        val snapshot = memberDoc(householdId, memberUserId).get().await()
        val relationship = snapshot.getString("relationship") ?: return null
        val status = snapshot.getString("status") ?: return null
        val notificationChannel = snapshot.getString("notification_channel") ?: "push"
        return FamilyMemberEntity(
            householdId = householdId,
            userId = memberUserId,
            relationship = relationship,
            permissionsJson = "{}",
            notificationChannel = notificationChannel,
            status = status,
        )
    }
}
