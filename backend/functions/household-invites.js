/**
 * Inviting someone to a Safety Circle by the email they signed up with.
 *
 * SafetyCircleRepository.inviteMember writes `households/{id}/members/{memberUserId}` directly,
 * which means the owner has to already know the invitee's Firebase UID. Nothing on a phone can
 * turn an email into a UID: firestore.rules grants no `list` on users (by design — see the
 * deny-by-default baseline), and Firebase Auth exposes no client-side lookup of other accounts,
 * deliberately, since that would let anyone probe which emails have accounts.
 *
 * So the lookup happens here, where the Admin SDK can do it and the caller can be checked first.
 * Ownership is verified against the household document before the email is ever resolved, so this
 * cannot be used as an "is this email registered?" oracle by someone who owns no household.
 *
 * The member document this writes is the same shape SafetyCircleRepository.inviteMember writes, so
 * accept/remove/roster all work on it unchanged, and the member_count trigger
 * (household-membership.js) sees an ordinary create.
 */
export async function inviteMemberByEmail({ db, auth, householdId, email, relationship, callerUserId }) {
  const household = await db.doc(`households/${householdId}`).get();
  if (!household.exists) {
    throw new Error('That Safety Circle no longer exists.');
  }
  if (household.data().owner_id !== callerUserId) {
    throw new Error('Only the owner of a Safety Circle can invite people to it.');
  }

  // Section 7's cap, checked here as well as in rules: the rules check runs on the member-doc
  // create below, but failing early gives the owner a sentence they can act on instead of a
  // permission-denied.
  if ((household.data().member_count ?? 0) >= 5) {
    throw new Error('A Safety Circle can have up to 5 people in it.');
  }

  let invitee;
  try {
    invitee = await auth.getUserByEmail(email);
  } catch {
    // Deliberately the same wording whatever went wrong: an owner learning "no account with that
    // email" is fine, but the message must not become a way to enumerate who has an account, so it
    // says what to do rather than confirming what exists.
    throw new Error('Nobody is using Ligaya with that email yet. Ask them to sign up first.');
  }

  if (invitee.uid === callerUserId) {
    throw new Error('You are already in your own Safety Circle.');
  }

  const memberDoc = db.doc(`households/${householdId}/members/${invitee.uid}`);
  if ((await memberDoc.get()).exists) {
    throw new Error('That person is already in this Safety Circle.');
  }

  await memberDoc.set({
    relationship,
    permissions: {},
    notification_channel: 'push',
    status: 'PENDING',
  });

  return { memberUserId: invitee.uid };
}
