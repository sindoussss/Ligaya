import test from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  confirmNotificationDelivery,
  dispatchFamilyAlerts,
  handleSmsDeliveryCallback,
} from '../family-alerts.js';

// `firebase emulators:exec` (see backend/package.json's test:functions script) injects
// FIRESTORE_EMULATOR_HOST automatically based on backend/firebase.json's emulator config — the
// Admin SDK picks it up on its own, no explicit host wiring needed here. This is the same
// "demo-*" project pattern as backend/test/firestore.rules.test.mjs and core-backend's own
// androidTest suite: no real Firebase project, login, or credentials involved.
const app = initializeApp({ projectId: 'demo-ligaya-test' });
const db = getFirestore(app);

function fakeMessaging(sendImpl) {
  return { send: sendImpl };
}

function fakeSmsGateway(sendImpl) {
  return { send: sendImpl };
}

const neverCalledMessaging = fakeMessaging(async () => {
  throw new Error('messaging.send must not be called in this test');
});

const neverCalledSmsGateway = fakeSmsGateway(async () => {
  throw new Error('smsGateway.send must not be called in this test');
});

async function seedHouseholdWithMembers(ownerId, members) {
  const householdRef = db.collection('households').doc();
  await householdRef.set({ owner_id: ownerId, subscription_state: {} });
  for (const member of members) {
    await householdRef.collection('members').doc(member.userId).set({
      relationship: 'sibling',
      permissions: {},
      notification_channel: 'push',
      status: member.status,
    });
  }
  return householdRef;
}

test('notifies only ACTIVE members, preferring push when a token is registered', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const activeWithToken = `active-with-token-${Date.now()}`;
  const activeNoChannel = `active-no-channel-${Date.now()}`;
  const pendingMember = `pending-${Date.now()}`;

  await seedHouseholdWithMembers(ownerId, [
    { userId: activeWithToken, status: 'ACTIVE' },
    { userId: activeNoChannel, status: 'ACTIVE' },
    { userId: pendingMember, status: 'PENDING' },
  ]);
  await db.collection('users').doc(activeWithToken).set({ fcm_token: 'token-123' });
  // activeNoChannel and pendingMember deliberately have no token and no phone number.

  const eventId = `event-${Date.now()}`;
  const sentTokens = [];
  const messaging = fakeMessaging(async (message) => {
    sentTokens.push(message.token);
    return 'fake-message-id';
  });

  const result = await dispatchFamilyAlerts({
    db,
    messaging,
    smsGateway: neverCalledSmsGateway,
    eventId,
    eventData: { user_id: ownerId, incident_type: 'FIRE', status: 'ACTIVE' },
  });

  assert.equal(result.notified, 2); // only the two ACTIVE members, never the PENDING one
  assert.deepEqual(sentTokens, ['token-123']);

  const notificationEvents = await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').get();
  assert.equal(notificationEvents.size, 2);

  const byMember = Object.fromEntries(
    notificationEvents.docs.map((doc) => [doc.get('member_user_id'), doc.data()]),
  );
  assert.equal(byMember[activeWithToken].status, 'SENT');
  assert.equal(byMember[activeWithToken].channel, 'push');
  assert.equal(byMember[activeNoChannel].status, 'FAILED');
  assert.equal(byMember[activeNoChannel].channel, 'none');
  assert.equal(byMember[activeNoChannel].reason, 'no-token-and-no-phone');
  assert.equal(byMember[pendingMember], undefined); // never notified at all
});

test('a member with no token but a registered phone number falls back to SMS', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `sms-member-${Date.now()}`;
  await seedHouseholdWithMembers(ownerId, [{ userId: memberId, status: 'ACTIVE' }]);
  await db.collection('users').doc(memberId).set({ phone_number: '+15551234567' });

  const eventId = `event-${Date.now()}`;
  const smsSends = [];
  const smsGateway = fakeSmsGateway(async (message) => {
    smsSends.push(message);
    return { messageId: 'SM_fake_123' };
  });

  const result = await dispatchFamilyAlerts({
    db,
    messaging: neverCalledMessaging,
    smsGateway,
    eventId,
    eventData: { user_id: ownerId, incident_type: 'FIRE', status: 'ACTIVE' },
  });

  assert.equal(result.notified, 1);
  assert.equal(smsSends.length, 1);
  assert.equal(smsSends[0].to, '+15551234567');
  assert.equal(smsSends[0].eventId, eventId);

  const notificationEvents = await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').get();
  assert.equal(notificationEvents.size, 1);
  const doc = notificationEvents.docs[0];
  assert.equal(doc.get('channel'), 'sms');
  assert.equal(doc.get('status'), 'SENT'); // gateway accepted it — not CONFIRMED yet
  assert.equal(doc.get('gateway_message_id'), 'SM_fake_123');
});

test('an SMS gateway failure is recorded as FAILED, not a false SENT', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `sms-member-fail-${Date.now()}`;
  await seedHouseholdWithMembers(ownerId, [{ userId: memberId, status: 'ACTIVE' }]);
  await db.collection('users').doc(memberId).set({ phone_number: '+15559876543' });

  const eventId = `event-${Date.now()}`;
  const smsGateway = fakeSmsGateway(async () => {
    throw new Error('invalid phone number');
  });

  await dispatchFamilyAlerts({
    db,
    messaging: neverCalledMessaging,
    smsGateway,
    eventId,
    eventData: { user_id: ownerId, incident_type: 'FIRE', status: 'ACTIVE' },
  });

  const notificationEvents = await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').get();
  assert.equal(notificationEvents.size, 1);
  assert.equal(notificationEvents.docs[0].get('channel'), 'sms');
  assert.equal(notificationEvents.docs[0].get('status'), 'FAILED');
  assert.match(notificationEvents.docs[0].get('reason'), /invalid phone number/);
});

test('a household with one push member and one SMS-fallback member notifies both independently', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const pushMember = `push-member-${Date.now()}`;
  const smsMember = `sms-member-${Date.now()}`;
  await seedHouseholdWithMembers(ownerId, [
    { userId: pushMember, status: 'ACTIVE' },
    { userId: smsMember, status: 'ACTIVE' },
  ]);
  await db.collection('users').doc(pushMember).set({ fcm_token: 'token-abc' });
  await db.collection('users').doc(smsMember).set({ phone_number: '+15550001111' });

  const eventId = `event-${Date.now()}`;
  const messaging = fakeMessaging(async () => 'fake-message-id');
  const smsGateway = fakeSmsGateway(async () => ({ messageId: 'SM_fake_456' }));

  const result = await dispatchFamilyAlerts({
    db,
    messaging,
    smsGateway,
    eventId,
    eventData: { user_id: ownerId, incident_type: 'FIRE', status: 'ACTIVE' },
  });

  assert.equal(result.notified, 2);
  const notificationEvents = await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').get();
  const byMember = Object.fromEntries(
    notificationEvents.docs.map((doc) => [doc.get('member_user_id'), doc.data()]),
  );
  assert.equal(byMember[pushMember].channel, 'push');
  assert.equal(byMember[pushMember].status, 'SENT');
  assert.equal(byMember[smsMember].channel, 'sms');
  assert.equal(byMember[smsMember].status, 'SENT');
});

test('a messaging send failure is recorded as FAILED, not left PENDING', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `member-${Date.now()}`;
  await seedHouseholdWithMembers(ownerId, [{ userId: memberId, status: 'ACTIVE' }]);
  await db.collection('users').doc(memberId).set({ fcm_token: 'stale-token' });

  const eventId = `event-${Date.now()}`;
  const messaging = fakeMessaging(async () => {
    throw new Error('registration-token-not-registered');
  });

  await dispatchFamilyAlerts({
    db,
    messaging,
    smsGateway: neverCalledSmsGateway,
    eventId,
    eventData: { user_id: ownerId, incident_type: 'FIRE', status: 'ACTIVE' },
  });

  const notificationEvents = await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').get();
  assert.equal(notificationEvents.size, 1);
  assert.equal(notificationEvents.docs[0].get('status'), 'FAILED');
  assert.match(notificationEvents.docs[0].get('reason'), /registration-token-not-registered/);
});

test('no household for the emergency owner notifies nobody, without error', async () => {
  const ownerWithNoHousehold = `lonely-${Date.now()}`;
  const eventId = `event-${Date.now()}`;

  const result = await dispatchFamilyAlerts({
    db,
    messaging: neverCalledMessaging,
    smsGateway: neverCalledSmsGateway,
    eventId,
    eventData: { user_id: ownerWithNoHousehold, incident_type: 'FIRE', status: 'ACTIVE' },
  });

  assert.equal(result.notified, 0);
});

test('handleSmsDeliveryCallback maps "delivered" to CONFIRMED', async () => {
  const eventId = `event-${Date.now()}`;
  const notificationRef = db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc();
  await notificationRef.set({ member_user_id: 'member-1', channel: 'sms', status: 'SENT' });

  const result = await handleSmsDeliveryCallback({
    db,
    eventId,
    notificationEventId: notificationRef.id,
    deliveryStatus: 'delivered',
  });

  assert.equal(result.updated, true);
  const updated = await notificationRef.get();
  assert.equal(updated.get('status'), 'CONFIRMED');
});

test('handleSmsDeliveryCallback maps "undelivered" and "failed" to FAILED', async () => {
  for (const deliveryStatus of ['undelivered', 'failed']) {
    const eventId = `event-${Date.now()}-${deliveryStatus}`;
    const notificationRef = db.collection('emergencyEvents').doc(eventId)
      .collection('notificationEvents').doc();
    await notificationRef.set({ member_user_id: 'member-1', channel: 'sms', status: 'SENT' });

    await handleSmsDeliveryCallback({ db, eventId, notificationEventId: notificationRef.id, deliveryStatus });

    const updated = await notificationRef.get();
    assert.equal(updated.get('status'), 'FAILED', `expected FAILED for gateway status "${deliveryStatus}"`);
  }
});

test('handleSmsDeliveryCallback ignores a non-terminal status like "queued"', async () => {
  const eventId = `event-${Date.now()}`;
  const notificationRef = db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc();
  await notificationRef.set({ member_user_id: 'member-1', channel: 'sms', status: 'SENT' });

  const result = await handleSmsDeliveryCallback({
    db,
    eventId,
    notificationEventId: notificationRef.id,
    deliveryStatus: 'queued',
  });

  assert.equal(result.updated, false);
  const unchanged = await notificationRef.get();
  assert.equal(unchanged.get('status'), 'SENT');
});

test('handleSmsDeliveryCallback on an unknown notification event reports not-found', async () => {
  const result = await handleSmsDeliveryCallback({
    db,
    eventId: `event-${Date.now()}`,
    notificationEventId: 'does-not-exist',
    deliveryStatus: 'delivered',
  });

  assert.equal(result.updated, false);
  assert.equal(result.reason, 'notification-event-not-found');
});

test('confirmNotificationDelivery transitions SENT to CONFIRMED for the addressed recipient', async () => {
  const eventId = `event-${Date.now()}`;
  const memberId = `member-${Date.now()}`;
  const notificationRef = db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc();
  await notificationRef.set({ member_user_id: memberId, channel: 'push', status: 'SENT' });

  await confirmNotificationDelivery({
    db,
    eventId,
    notificationEventId: notificationRef.id,
    callerUserId: memberId,
  });

  const updated = await notificationRef.get();
  assert.equal(updated.get('status'), 'CONFIRMED');
});

test('confirmNotificationDelivery rejects a caller who is not the addressed recipient', async () => {
  const eventId = `event-${Date.now()}`;
  const memberId = `member-${Date.now()}`;
  const impostor = `impostor-${Date.now()}`;
  const notificationRef = db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc();
  await notificationRef.set({ member_user_id: memberId, channel: 'push', status: 'SENT' });

  await assert.rejects(
    () => confirmNotificationDelivery({
      db,
      eventId,
      notificationEventId: notificationRef.id,
      callerUserId: impostor,
    }),
    /not-the-addressed-recipient/,
  );

  const unchanged = await notificationRef.get();
  assert.equal(unchanged.get('status'), 'SENT');
});

test.after(async () => {
  await Promise.all(getApps().map((a) => deleteApp(a)));
});
