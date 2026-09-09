const ACTIVE_STATUS = 'ACTIVE';

/**
 * Computes section 18's Family emergency screen field set, already filtered server-side per the
 * viewer's own permissions — section 27's "client UI is never trusted as the sole authorization
 * mechanism" applied literally: a field the caller isn't permitted to see is never present in the
 * returned object at all, not merely left for the client to hide. index.js's callable wrapper is
 * the only way a client reaches this; there is no Firestore rule that lets a client read the raw
 * emergencyEvents/locationEvents documents directly for someone else's emergency, so this
 * function is the only path to that data at all.
 *
 * The emergency owner always sees the full view of their own emergency — permission gating only
 * applies to other Safety Circle members, and specifically only to location (section 18's own
 * example screen shows incident type/time/status/alert state unconditionally; only location is
 * called out anywhere as something a sender can withhold). A PENDING (not yet accepted) invite or
 * a complete stranger gets nothing at all: this throws rather than returning a redacted view, so
 * a client can't even confirm an emergency exists for an event ID it isn't entitled to know about.
 */
export async function computeFamilyEmergencyView({ db, eventId, callerUserId }) {
  const eventSnapshot = await db.collection('emergencyEvents').doc(eventId).get();
  if (!eventSnapshot.exists) {
    throw new Error('emergency-event-not-found');
  }
  const eventData = eventSnapshot.data();
  const isOwner = eventData.user_id === callerUserId;

  let canViewLocation = isOwner;
  if (!isOwner) {
    const householdQuery = await db.collection('households')
      .where('owner_id', '==', eventData.user_id)
      .limit(1)
      .get();
    if (householdQuery.empty) {
      throw new Error('not-authorized');
    }
    const memberSnapshot = await householdQuery.docs[0].ref.collection('members').doc(callerUserId).get();
    if (!memberSnapshot.exists || memberSnapshot.get('status') !== ACTIVE_STATUS) {
      throw new Error('not-authorized');
    }
    canViewLocation = memberSnapshot.get('permissions')?.can_view_location === true;
  }

  const notificationQuery = await db.collection('emergencyEvents').doc(eventId)
    .collection('notificationEvents')
    .where('member_user_id', '==', callerUserId)
    .orderBy('created_at', 'desc')
    .limit(1)
    .get();
  const alertState = notificationQuery.empty ? null : notificationQuery.docs[0].get('status');

  const view = {
    incidentType: eventData.incident_type,
    // A raw Firestore Timestamp doesn't survive the callable's JSON response as a usable value —
    // converted to epoch millis explicitly so the client gets a plain number, not an
    // implementation detail of the Admin SDK it would have to know how to parse.
    time: eventData.created_at?.toMillis() ?? null,
    status: eventData.status,
    alertState,
  };

  // Key omitted entirely when not permitted — not set to null — so the raw response payload
  // itself never carries a "location" field for a caller who isn't allowed to see one, matching
  // this step's own acceptance criterion ("never receives location data ... even if they inspect
  // network traffic").
  if (canViewLocation) {
    const locationQuery = await db.collection('emergencyEvents').doc(eventId)
      .collection('locationEvents')
      .orderBy('created_at', 'desc')
      .limit(1)
      .get();
    view.location = locationQuery.empty
      ? null
      : {
        latitude: locationQuery.docs[0].get('latitude'),
        longitude: locationQuery.docs[0].get('longitude'),
      };
  }

  return view;
}
