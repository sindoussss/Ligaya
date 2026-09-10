import { FieldValue } from 'firebase-admin/firestore';

/**
 * Step 53's own conformance-audit finding: LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 7 says
 * the family plan is "up to 5 invited members - limit enforced by Ligaya backend," but nothing
 * enforced it anywhere — SafetyCircleRepository.inviteMember (core-backend) writes a member doc
 * unconditionally, and firestore.rules' members/{memberUserId} create rule only ever checked
 * ownership, never a count. Firestore security rules cannot count a subcollection's documents
 * directly (no aggregation over an arbitrary query in rules language), so — same as any other
 * Firestore app enforcing this kind of cap — a trusted Cloud Function trigger maintains a
 * denormalized `member_count` on the household document, and the rule (see firestore.rules)
 * checks that count before allowing a new member doc to be created. The count includes PENDING
 * invites, not just ACTIVE members: section 7 says "invited," and an invite creates the member
 * doc immediately regardless of whether it's later accepted — matching how
 * SafetyCircleRepository.getMembers() already returns every member doc regardless of status.
 *
 * Deliberately counts create/delete only, not update (e.g. PENDING -> ACTIVE on accept, Step 8):
 * accepting an existing invite doesn't change how many member docs exist, so it must never move
 * the counter — only whether the document exists at all, before vs. after this write, matters.
 */
export async function updateMemberCount({ db, householdId, existedBefore, existsAfter }) {
  if (existedBefore === existsAfter) {
    return { changed: false };
  }
  const delta = existsAfter ? 1 : -1;
  await db.doc(`households/${householdId}`).update({ member_count: FieldValue.increment(delta) });
  return { changed: true, delta };
}
