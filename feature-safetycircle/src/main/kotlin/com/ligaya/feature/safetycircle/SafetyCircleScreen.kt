package com.ligaya.feature.safetycircle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ligaya.core.backend.household.InviteResult
import com.ligaya.core.backend.household.SafetyCircleRepository
import com.ligaya.core.data.entity.FamilyMemberEntity
import com.ligaya.core.data.entity.FamilyMemberStatus
import com.ligaya.designsystem.LigayaTheme
import com.ligaya.designsystem.LigayaSpacing
import com.ligaya.designsystem.LigayaTypography
import com.ligaya.designsystem.components.StatusChip
import com.ligaya.designsystem.components.StatusTone
import kotlinx.coroutines.launch

/**
 * Step 43's Safety Circle management screen (§6). "Reflects real backend membership state from
 * Step 8 with no client-side-only assumptions" is why this screen has two real, different views
 * rather than one generic member list: backend/firestore.rules only lets the household OWNER list
 * the full roster (see [SafetyCircleRepository.getMembers]'s own doc comment) — an ordinary
 * member can only ever read their own membership record. Showing a "member list" to a non-owner
 * would mean fabricating data the backend never actually grants, so a non-owner instead sees and
 * manages their own membership (accept the invite, or leave), which the backend genuinely does
 * allow them to do.
 *
 * "installed/not installed" from §27's screen-inventory wording is deliberately not rendered:
 * no field in Step 8's FAMILY_MEMBER model (or anywhere reachable by a household member/owner's
 * Firestore rules) carries that signal — showing it would mean inventing client-side-only state,
 * exactly what this step's acceptance criterion rules out. Status (PENDING/ACTIVE, the real field
 * that exists) is shown instead.
 */
@Composable
fun SafetyCircleScreen(
    householdId: String,
    currentUserId: String,
    repository: SafetyCircleRepository,
    modifier: Modifier = Modifier,
) {
    var isOwner by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf<List<FamilyMemberEntity>>(emptyList()) }
    var ownMembership by remember { mutableStateOf<FamilyMemberEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var inviting by remember { mutableStateOf(false) }
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val household = repository.getHousehold(householdId)
        isOwner = household?.ownerId == currentUserId
        if (isOwner) {
            members = repository.getMembers(householdId)
        } else {
            ownMembership = repository.getMember(householdId, currentUserId)
        }
        isLoading = false
    }

    LaunchedEffect(householdId, currentUserId) { refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LigayaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LigayaSpacing.md),
    ) {
        Text(text = "Safety Circle", style = LigayaTypography.headline, color = LigayaTheme.colors.onSurface)

        if (isLoading) {
            Text(text = "Loading…", style = LigayaTypography.body, color = LigayaTheme.colors.onSurface)
        } else if (isOwner) {
            RosterSection(
                members = members,
                onRemove = { userId -> scope.launch { repository.removeMember(householdId, userId); refresh() } },
            )
            HorizontalDivider()
            InviteSection(
                inviting = inviting,
                inviteStatus = inviteStatus,
                onInvite = { email, relationship ->
                    inviting = true
                    inviteStatus = null
                    scope.launch {
                        when (val result = repository.inviteMemberByEmail(householdId, email, relationship)) {
                            is InviteResult.Invited -> {
                                inviteStatus = "Invited. They will see it when they next open Ligaya."
                                refresh()
                            }
                            is InviteResult.Failed -> inviteStatus = result.message
                        }
                        inviting = false
                    }
                },
            )
        } else {
            OwnMembershipSection(
                membership = ownMembership,
                onAccept = { scope.launch { repository.acceptInvite(householdId, currentUserId); refresh() } },
                onLeave = { scope.launch { repository.removeMember(householdId, currentUserId); refresh() } },
            )
        }
    }
}

@Composable
private fun RosterSection(members: List<FamilyMemberEntity>, onRemove: (userId: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LigayaSpacing.sm)) {
        Text(text = "Members", style = LigayaTypography.label, color = LigayaTheme.colors.onSurface)
        if (members.isEmpty()) {
            Text(text = "No members yet", style = LigayaTypography.body, color = LigayaTheme.colors.onSurface)
        }
        members.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${member.relationship}, ${statusLabel(member.status)}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LigayaSpacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = member.relationship, style = LigayaTypography.body, color = LigayaTheme.colors.onSurface)
                    Text(text = member.userId, style = LigayaTypography.label, color = LigayaTheme.colors.onSurface)
                }
                StatusChip(text = statusLabel(member.status), tone = statusTone(member.status))
                Button(
                    modifier = Modifier.testTag("safetyCircleRemove_${member.userId}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LigayaTheme.colors.colorStatusFailed,
                        contentColor = LigayaTheme.colors.onStatusFailed,
                    ),
                    onClick = { onRemove(member.userId) },
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

/**
 * Invites go by email, which is the only thing an owner actually knows about the person they want
 * to add — a Firebase user id is not something anyone can read off their own phone, let alone tell
 * someone else. The lookup happens in the `inviteToSafetyCircle` Cloud Function, so [inviteStatus]
 * carries back whatever it said, including "nobody is using Ligaya with that email yet".
 */
@Composable
private fun InviteSection(
    inviting: Boolean,
    inviteStatus: String?,
    onInvite: (email: String, relationship: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(LigayaSpacing.sm)) {
        Text(text = "Invite a member", style = LigayaTypography.label, color = LigayaTheme.colors.onSurface)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Their email address") },
            singleLine = true,
            enabled = !inviting,
            modifier = Modifier.fillMaxWidth().testTag("safetyCircleInviteEmail"),
        )
        OutlinedTextField(
            value = relationship,
            onValueChange = { relationship = it },
            label = { Text("Relationship (e.g. Mother, Friend)") },
            singleLine = true,
            enabled = !inviting,
            modifier = Modifier.fillMaxWidth().testTag("safetyCircleInviteRelationship"),
        )
        Button(
            enabled = !inviting && email.isNotBlank() && relationship.isNotBlank(),
            modifier = Modifier.testTag("safetyCircleSendInvite"),
            onClick = {
                onInvite(email, relationship)
                email = ""
                relationship = ""
            },
        ) {
            Text(if (inviting) "Sending…" else "Send Invite")
        }
        if (inviteStatus != null) {
            Text(
                text = inviteStatus,
                style = LigayaTypography.body,
                color = LigayaTheme.colors.onSurface,
                modifier = Modifier.semantics { contentDescription = "Invite: $inviteStatus" },
            )
        }
    }
}

@Composable
private fun OwnMembershipSection(
    membership: FamilyMemberEntity?,
    onAccept: () -> Unit,
    onLeave: () -> Unit,
) {
    if (membership == null) {
        Text(text = "You're not a member of this Safety Circle.", style = LigayaTypography.body, color = LigayaTheme.colors.onSurface)
        return
    }

    Text(
        text = "You're the ${membership.relationship} in this Safety Circle.",
        style = LigayaTypography.body,
        color = LigayaTheme.colors.onSurface,
    )
    StatusChip(text = statusLabel(membership.status), tone = statusTone(membership.status))

    when (membership.status) {
        FamilyMemberStatus.PENDING -> Button(
            modifier = Modifier.testTag("safetyCircleAcceptInvite"),
            onClick = onAccept,
        ) {
            Text("Accept Invite")
        }
        FamilyMemberStatus.ACTIVE -> Button(
            modifier = Modifier.testTag("safetyCircleLeave"),
            colors = ButtonDefaults.buttonColors(
                containerColor = LigayaTheme.colors.colorStatusFailed,
                contentColor = LigayaTheme.colors.onStatusFailed,
            ),
            onClick = onLeave,
        ) {
            Text("Leave Safety Circle")
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    FamilyMemberStatus.PENDING -> "Pending"
    FamilyMemberStatus.ACTIVE -> "Active"
    else -> status
}

private fun statusTone(status: String): StatusTone = when (status) {
    FamilyMemberStatus.PENDING -> StatusTone.Pending
    FamilyMemberStatus.ACTIVE -> StatusTone.Success
    else -> StatusTone.Neutral
}
