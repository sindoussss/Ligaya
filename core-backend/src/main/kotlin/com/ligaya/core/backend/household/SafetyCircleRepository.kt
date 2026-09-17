package com.ligaya.core.backend.household

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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

    /**
     * The household this user owns, created the first time they need one.
     *
     * The id is kept on the user's own document rather than found by querying households for
     * `owner_id == ownerId`, because firestore.rules deliberately grants no `list` permission on
     * the households collection — a query cannot prove in advance that every possible result is
     * readable, so Firestore rejects it outright. `users/{userId}` is readable and writable by
     * that user alone, which is exactly the scope this needs.
     */
    suspend fun getOrCreateOwnedHousehold(ownerId: String): String
    suspend fun getHousehold(householdId: String): HouseholdEntity?
    suspend fun inviteMember(householdId: String, memberUserId: String, relationship: String)

    /**
     * Invite by the email the person signed up with, which is the only identifier an owner
     * actually knows. Runs through the `inviteToSafetyCircle` Cloud Function rather than writing
     * the member document from here: turning an email into a user id needs the Admin SDK, since
     * neither firestore.rules nor Firebase Auth will let one client look up another account (see
     * backend/functions/household-invites.js).
     *
     * Returns the invited user's id on success, or a [InviteFailure] carrying a message written
     * for the person reading it.
     */
    suspend fun inviteMemberByEmail(householdId: String, email: String, relationship: String): InviteResult
    suspend fun acceptInvite(householdId: String, memberUserId: String)
    suspend fun removeMember(householdId: String, memberUserId: String)
    suspend fun getMember(householdId: String, memberUserId: String): FamilyMemberEntity?

    /** Step 43's own addition: the full roster, for the Safety Circle management screen. Per
     *  backend/firestore.rules' own comment on the members/{memberUserId} read rule ("full roster
     *  visibility for ordinary members is a deliberately separate, later decision"), this only
     *  ever succeeds for the household owner — Firestore rejects a `list` query outright (it
     *  cannot prove the rule holds for every possible result) rather than silently filtering to
     *  just the caller's own document, so a non-owner calling this gets a real thrown exception,
     *  not an empty list standing in for "no members." Callers must not treat those the same. */
    suspend fun getMembers(householdId: String): List<FamilyMemberEntity>
}

sealed interface InviteResult {
    data class Invited(val memberUserId: String) : InviteResult

    /** [message] is already fit to show: the function returns sentences for the owner to read. */
    data class Failed(val message: String) : InviteResult
}

private const val HOUSEHOLD_ID_FIELD = "household_id"

class FirestoreSafetyCircleRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions? = null,
) : SafetyCircleRepository {

    override suspend fun inviteMemberByEmail(
        householdId: String,
        email: String,
        relationship: String,
    ): InviteResult {
        val callable = functions
            ?: return InviteResult.Failed("Inviting people needs a connection to Ligaya. Try again in a moment.")
        return runCatching {
            val response = callable.getHttpsCallable("inviteToSafetyCircle")
                .call(mapOf("householdId" to householdId, "email" to email.trim(), "relationship" to relationship))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = response.getData() as? Map<String, Any?>
            val memberUserId = data?.get("memberUserId") as? String
                ?: return InviteResult.Failed("That invite did not go through. Please try again.")
            InviteResult.Invited(memberUserId)
        }.getOrElse { error ->
            // The function raises failed-precondition with a sentence meant for the owner ("Nobody
            // is using Ligaya with that email yet"), so that message is shown as-is when there is
            // one. Anything else gets a plain fallback instead of a raw exception string.
            InviteResult.Failed(
                (error as? FirebaseFunctionsException)?.message?.takeIf { it.isNotBlank() }
                    ?: "That invite did not go through. Please try again.",
            )
        }
    }

    private fun householdDoc(householdId: String) = firestore.collection("households").document(householdId)
    private fun memberDoc(householdId: String, memberUserId: String) =
        householdDoc(householdId).collection("members").document(memberUserId)

    override suspend fun createHousehold(ownerId: String): String {
        val ref = firestore.collection("households").document()
        // member_count: 0 (Step 53's own finding) is what firestore.rules' members/{id} create
        // rule checks against section 7's "up to 5 invited members" cap — a Cloud Function
        // trigger (household-membership.js) keeps it in sync on every invite/removal from here.
        // The rule itself also defaults a missing count to 0, so this is belt-and-suspenders
        // clarity for a household created through this exact path, not the only thing making it
        // safe.
        ref.set(
            mapOf(
                "owner_id" to ownerId,
                "subscription_state" to emptyMap<String, Any>(),
                "member_count" to 0,
            ),
        ).await()
        return ref.id
    }

    override suspend fun getOrCreateOwnedHousehold(ownerId: String): String {
        val userDoc = firestore.collection("users").document(ownerId)
        userDoc.get().await().getString(HOUSEHOLD_ID_FIELD)?.let { return it }
        val householdId = createHousehold(ownerId)
        userDoc.set(mapOf(HOUSEHOLD_ID_FIELD to householdId), SetOptions.merge()).await()
        return householdId
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

    override suspend fun getMember(householdId: String, memberUserId: String): FamilyMemberEntity? =
        parseMember(householdId, memberDoc(householdId, memberUserId).get().await())

    override suspend fun getMembers(householdId: String): List<FamilyMemberEntity> {
        val snapshot = householdDoc(householdId).collection("members").get().await()
        return snapshot.documents.mapNotNull { doc -> parseMember(householdId, doc) }
    }

    /** Shared by [getMember] (a single document read) and [getMembers] (a collection query) —
     *  same fields, same defaulting, one parsing path rather than two that could quietly drift. */
    private fun parseMember(householdId: String, snapshot: DocumentSnapshot): FamilyMemberEntity? {
        val relationship = snapshot.getString("relationship") ?: return null
        val status = snapshot.getString("status") ?: return null
        val notificationChannel = snapshot.getString("notification_channel") ?: "push"
        return FamilyMemberEntity(
            householdId = householdId,
            userId = snapshot.id,
            relationship = relationship,
            permissionsJson = "{}",
            notificationChannel = notificationChannel,
            status = status,
        )
    }
}
