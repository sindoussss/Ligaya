import test from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, getApps, initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { computeFamilyEmergencyView } from '../family-emergency-view.js';

// Same "demo-*" local-emulator pattern as family-alerts.test.mjs — no real Firebase project.
const app = initializeApp({ projectId: 'demo-ligaya-test' });
const db = getFirestore(app);

async function seedEmergencyEvent({ eventId, ownerId, incidentType = 'FIRE', status = 'ACTIVE' }) {
  await db.collection('emergencyEvents').doc(eventId).set({
    user_id: ownerId,
    incident_type: incidentType,
    status,
    created_at: FieldValue.serverTimestamp(),
  });
}

async function seedHouseholdWithMember({ ownerId, memberId, memberStatus = 'ACTIVE', canViewLocation = false }) {
  const householdRef = db.collection('households').doc();
  await householdRef.set({ owner_id: ownerId, subscription_state: {} });
  await householdRef.collection('members').doc(memberId).set({
    relationship: 'sibling',
    permissions: { can_view_location: canViewLocation },
    notification_channel: 'push',
    status: memberStatus,
  });
  return householdRef;
}

async function seedLocationEvent(eventId, { latitude, longitude }) {
  await db.collection('emergencyEvents').doc(eventId)
    .collection('locationEvents').doc()
    .set({ latitude, longitude, created_at: FieldValue.serverTimestamp() });
}

async function seedNotificationEvent(eventId, { memberUserId, status }) {
  await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents').doc()
    .set({ member_user_id: memberUserId, channel: 'push', status, created_at: FieldValue.serverTimestamp() });
}

test('the emergency owner sees the full view, including location', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedLocationEvent(eventId, { latitude: 14.5995, longitude: 120.9842 });

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: ownerId });

  assert.equal(view.incidentType, 'FIRE');
  assert.equal(view.status, 'ACTIVE');
  assert.deepEqual(view.location, { latitude: 14.5995, longitude: 120.9842 });
  // Must be a plain epoch-millis number, not a raw Firestore Timestamp object — a client on the
  // other side of the callable JSON boundary has no way to parse the latter.
  assert.equal(typeof view.time, 'number');
  assert.ok(view.time > 0);
});

test('a member WITH location permission sees location data', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `member-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedHouseholdWithMember({ ownerId, memberId, canViewLocation: true });
  await seedLocationEvent(eventId, { latitude: 10.3, longitude: 123.9 });

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: memberId });

  assert.deepEqual(view.location, { latitude: 10.3, longitude: 123.9 });
});

test('a member WITHOUT location permission never receives a location field at all', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `member-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedHouseholdWithMember({ ownerId, memberId, canViewLocation: false });
  await seedLocationEvent(eventId, { latitude: 10.3, longitude: 123.9 }); // real data exists...

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: memberId });

  // ...but the response object itself must never carry the key — not merely a null value, which
  // a client could still be trusted (or not) to hide. This is the literal acceptance criterion:
  // inspecting the raw response can't reveal location data.
  assert.equal('location' in view, false);
  // The rest of the field set is still visible — only location is gated.
  assert.equal(view.incidentType, 'FIRE');
  assert.equal(view.status, 'ACTIVE');
});

test('a member with no permissions field at all defaults to no location access', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `member-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  const householdRef = db.collection('households').doc();
  await householdRef.set({ owner_id: ownerId, subscription_state: {} });
  await householdRef.collection('members').doc(memberId).set({
    relationship: 'sibling',
    status: 'ACTIVE',
    // permissions deliberately omitted entirely.
  });

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: memberId });

  assert.equal('location' in view, false);
});

test('a permitted member with no location recorded yet gets location: null, not an error', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `member-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedHouseholdWithMember({ ownerId, memberId, canViewLocation: true });
  // No location event seeded at all.

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: memberId });

  assert.equal('location' in view, true);
  assert.equal(view.location, null);
});

test('returns the most recently recorded location, not the first one written', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedLocationEvent(eventId, { latitude: 1.0, longitude: 1.0 });
  await seedLocationEvent(eventId, { latitude: 2.0, longitude: 2.0 });
  await seedLocationEvent(eventId, { latitude: 3.0, longitude: 3.0 });

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: ownerId });

  assert.deepEqual(view.location, { latitude: 3.0, longitude: 3.0 });
});

test('a PENDING (not yet accepted) member is rejected, not given a redacted view', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `pending-member-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedHouseholdWithMember({ ownerId, memberId, memberStatus: 'PENDING', canViewLocation: true });

  await assert.rejects(
    () => computeFamilyEmergencyView({ db, eventId, callerUserId: memberId }),
    /not-authorized/,
  );
});

test('a complete stranger is rejected, not given a redacted view', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const stranger = `stranger-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });

  await assert.rejects(
    () => computeFamilyEmergencyView({ db, eventId, callerUserId: stranger }),
    /not-authorized/,
  );
});

test('an unknown emergency event id is rejected', async () => {
  await assert.rejects(
    () => computeFamilyEmergencyView({ db, eventId: 'does-not-exist', callerUserId: 'anyone' }),
    /emergency-event-not-found/,
  );
});

test('alertState reflects the caller\'s own most recent notification event', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const memberId = `member-${Date.now()}`;
  const otherMemberId = `other-member-${Date.now()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });
  await seedHouseholdWithMember({ ownerId, memberId, canViewLocation: false });
  await seedNotificationEvent(eventId, { memberUserId: otherMemberId, status: 'FAILED' });
  await seedNotificationEvent(eventId, { memberUserId: memberId, status: 'SENT' });
  await seedNotificationEvent(eventId, { memberUserId: memberId, status: 'CONFIRMED' });

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: memberId });

  assert.equal(view.alertState, 'CONFIRMED'); // the caller's own latest, never another member's
});

test('alertState is null when the caller has no notification event on record', async () => {
  const ownerId = `owner-${Date.now()}-${Math.random()}`;
  const eventId = `event-${Date.now()}`;
  await seedEmergencyEvent({ eventId, ownerId });

  const view = await computeFamilyEmergencyView({ db, eventId, callerUserId: ownerId });

  assert.equal(view.alertState, null);
});

test.after(async () => {
  await Promise.all(getApps().map((a) => deleteApp(a)));
});
