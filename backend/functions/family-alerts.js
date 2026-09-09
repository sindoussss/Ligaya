import { FieldValue } from 'firebase-admin/firestore';

const ACTIVE_STATUS = 'ACTIVE';

/**
 * Core fan-out logic for section 17's family alert (implementation Steps 18/19): find the
 * emergency owner's household and, for each ACTIVE Safety Circle member, notify them on whichever
 * channel actually reaches them — push if they have a registered FCM token (the app has run on
 * some device), else SMS via the cloud gateway (Issue D's resolution: a backend gateway, not
 * on-device SmsManager, so delivery-receipt tracking is possible and the sending device's own
 * signal/SMS credit is never a dependency) if they have a registered phone number, else FAILED
 * with no send attempt at all. Records a PENDING -> SENT|FAILED (or -> CONFIRMED once
 * acknowledged/delivered) NOTIFICATION_EVENT per member, per section 17's own rule that a channel
 * is never collapsed into one "family notified" claim.
 *
 * Deliberately takes `db`/`messaging`/`smsGateway` as parameters rather than importing the Admin
 * SDK singletons or a concrete gateway client directly, so tests can supply a real Firestore
 * emulator client alongside fakes for both external services — there is no local emulator for
 * Cloud Messaging or for any SMS gateway, so this dependency-injection seam is the only way to
 * test the fan-out and state-tracking logic without real accounts and a real device/phone
 * number. index.js wires the real `getFirestore()`/`getMessaging()`/`createTwilioSmsGateway()`
 * for the deployed function.
 *
 * "Without the app installed" (this step's own acceptance language) is read here as "no FCM
 * token on record" — a member always has a Ligaya account by the time they can be invited at all
 * (SafetyCircleRepository.inviteMember takes an existing user's uid, Step 8), so the real
 * distinguishing signal available to this function is whether their device has ever registered a
 * push token, not whether an account exists.
 */
export async function dispatchFamilyAlerts({ db, messaging, smsGateway, eventId, eventData }) {
  const householdQuery = await db.collection('households')
    .where('owner_id', '==', eventData.user_id)
    .limit(1)
    .get();

  if (householdQuery.empty) {
    return { notified: 0, reason: 'no-household' };
  }
  const household = householdQuery.docs[0];

  const membersQuery = await household.ref.collection('members')
    .where('status', '==', ACTIVE_STATUS)
    .get();

  const results = [];
  for (const memberDoc of membersQuery.docs) {
    const memberUserId = memberDoc.id;
    const notificationRef = db.collection('emergencyEvents').doc(eventId)
      .collection('notificationEvents').doc();

    const userSnapshot = await db.collection('users').doc(memberUserId).get();
    const token = userSnapshot.get('fcm_token');
    const phoneNumber = userSnapshot.get('phone_number');

    if (token) {
      await notificationRef.set({
        member_user_id: memberUserId,
        channel: 'push',
        status: 'PENDING',
        created_at: FieldValue.serverTimestamp(),
        updated_at: FieldValue.serverTimestamp(),
      });
      try {
        await messaging.send({
          token,
          notification: {
            title: 'Ligaya emergency alert',
            body: 'A member of your Safety Circle has activated an emergency.',
          },
          data: {
            eventId,
            notificationEventId: notificationRef.id,
          },
        });
        await notificationRef.update({ status: 'SENT', updated_at: FieldValue.serverTimestamp() });
        results.push({ memberUserId, status: 'SENT', channel: 'push' });
      } catch (error) {
        await notificationRef.update({
          status: 'FAILED',
          reason: String(error?.message ?? error),
          updated_at: FieldValue.serverTimestamp(),
        });
        results.push({ memberUserId, status: 'FAILED', channel: 'push' });
      }
      continue;
    }

    if (phoneNumber) {
      await notificationRef.set({
        member_user_id: memberUserId,
        channel: 'sms',
        status: 'PENDING',
        created_at: FieldValue.serverTimestamp(),
        updated_at: FieldValue.serverTimestamp(),
      });
      try {
        const sendResult = await smsGateway.send({
          to: phoneNumber,
          body: 'Ligaya emergency alert: a member of your Safety Circle has activated an emergency.',
          eventId,
          notificationEventId: notificationRef.id,
        });
        // SENT here means only "the gateway accepted the message," matching this step's own
        // acceptance criterion that a gateway failure produces FAILED, not a false SENT — actual
        // far-end delivery only ever moves this to CONFIRMED via handleSmsDeliveryCallback below,
        // never assumed from a successful send alone.
        await notificationRef.update({
          status: 'SENT',
          gateway_message_id: sendResult.messageId,
          updated_at: FieldValue.serverTimestamp(),
        });
        results.push({ memberUserId, status: 'SENT', channel: 'sms' });
      } catch (error) {
        await notificationRef.update({
          status: 'FAILED',
          reason: String(error?.message ?? error),
          updated_at: FieldValue.serverTimestamp(),
        });
        results.push({ memberUserId, status: 'FAILED', channel: 'sms' });
      }
      continue;
    }

    await notificationRef.set({
      member_user_id: memberUserId,
      channel: 'none',
      status: 'FAILED',
      reason: 'no-token-and-no-phone',
      created_at: FieldValue.serverTimestamp(),
      updated_at: FieldValue.serverTimestamp(),
    });
    results.push({ memberUserId, status: 'FAILED', channel: 'none' });
  }

  return { notified: results.length, results };
}

const SMS_DELIVERY_STATUS_TO_NOTIFICATION_STATE = {
  delivered: 'CONFIRMED',
  undelivered: 'FAILED',
  failed: 'FAILED',
};

/**
 * Handles the SMS gateway's delivery-receipt callback (this step's own acceptance criterion:
 * "delivery-receipt callback ... updates state correctly"). `eventId`/`notificationEventId` are
 * round-tripped through the gateway's own callback-URL mechanism (see
 * createTwilioSmsGateway.send's StatusCallback URL) rather than looked up by gateway message ID,
 * matching how the FCM push payload round-trips the same two IDs (Step 18) — both avoid needing
 * a Firestore query (or a collection-group index) just to find which document a callback is
 * about.
 *
 * An unrecognized status (Twilio has several in-flight ones like "queued"/"sent" that aren't a
 * final delivery outcome yet) is deliberately a no-op, not a FAILED — only a definite terminal
 * status changes anything.
 */
export async function handleSmsDeliveryCallback({ db, eventId, notificationEventId, deliveryStatus }) {
  const mappedState = SMS_DELIVERY_STATUS_TO_NOTIFICATION_STATE[deliveryStatus];
  if (!mappedState) {
    return { updated: false, reason: 'not-a-terminal-status' };
  }

  const ref = db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc(notificationEventId);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    return { updated: false, reason: 'notification-event-not-found' };
  }

  await ref.update({ status: mappedState, updated_at: FieldValue.serverTimestamp() });
  return { updated: true };
}

/**
 * The trusted CONFIRMED write (section 17): only reachable once the recipient's own device
 * acknowledges the push it actually received, for the specific notification event addressed to
 * them — never a bare client write, per firestore.rules' own note that NOTIFICATION_EVENT state
 * transitions are backend-only. `callerUserId` must already be the caller's verified auth uid
 * (index.js's callable wrapper is responsible for that); this function still re-checks it
 * against the notification event's own recipient before writing, rather than trusting the
 * caller not to pass someone else's IDs — section 27's "client is never the sole authorization
 * mechanism" applies to this function's own inputs too, not just Firestore rules.
 */
export async function confirmNotificationDelivery({ db, eventId, notificationEventId, callerUserId }) {
  const ref = db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc(notificationEventId);
  const snapshot = await ref.get();

  if (!snapshot.exists) {
    throw new Error('notification-event-not-found');
  }
  if (snapshot.get('member_user_id') !== callerUserId) {
    throw new Error('not-the-addressed-recipient');
  }

  await ref.update({ status: 'CONFIRMED', updated_at: FieldValue.serverTimestamp() });
}
