import test from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { updateMemberCount } from '../household-membership.js';

// Same "demo-*" emulator-only pattern as family-alerts.test.mjs — no real project/credentials.
const app = initializeApp({ projectId: 'demo-ligaya-test' });
const db = getFirestore(app);

async function seedHousehold(memberCount) {
  const ref = db.collection('households').doc();
  await ref.set({ owner_id: 'owner', subscription_state: {}, member_count: memberCount });
  return ref;
}

test('a create (existedBefore: false, existsAfter: true) increments member_count by 1', async () => {
  const ref = await seedHousehold(2);

  const result = await updateMemberCount({ db, householdId: ref.id, existedBefore: false, existsAfter: true });

  assert.equal(result.changed, true);
  const snap = await ref.get();
  assert.equal(snap.data().member_count, 3);
});

test('a delete (existedBefore: true, existsAfter: false) decrements member_count by 1', async () => {
  const ref = await seedHousehold(3);

  await updateMemberCount({ db, householdId: ref.id, existedBefore: true, existsAfter: false });

  const snap = await ref.get();
  assert.equal(snap.data().member_count, 2);
});

test('an update (existedBefore: true, existsAfter: true, e.g. accepting an invite) leaves member_count unchanged', async () => {
  const ref = await seedHousehold(3);

  const result = await updateMemberCount({ db, householdId: ref.id, existedBefore: true, existsAfter: true });

  assert.equal(result.changed, false);
  const snap = await ref.get();
  assert.equal(snap.data().member_count, 3);
});

test('concurrent creates each increment exactly once (FieldValue.increment is atomic, no lost updates)', async () => {
  const ref = await seedHousehold(0);

  await Promise.all(
    Array.from({ length: 5 }, () =>
      updateMemberCount({ db, householdId: ref.id, existedBefore: false, existsAfter: true })),
  );

  const snap = await ref.get();
  assert.equal(snap.data().member_count, 5);
});
