import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentWritten } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall, onRequest } from 'firebase-functions/v2/https';
import { defineSecret } from 'firebase-functions/params';
import {
  confirmNotificationDelivery,
  dispatchFamilyAlerts,
  handleSmsDeliveryCallback,
} from './family-alerts.js';
import { createTwilioSmsGateway, isValidTwilioSignature } from './twilio-sms-gateway.js';
import { computeFamilyEmergencyView } from './family-emergency-view.js';

initializeApp();

// Real Twilio account credentials (Issue D's resolution — see twilio-sms-gateway.js). Set via
// `firebase functions:secrets:set TWILIO_ACCOUNT_SID` etc. before deploying; never hardcoded.
const twilioAccountSid = defineSecret('TWILIO_ACCOUNT_SID');
const twilioAuthToken = defineSecret('TWILIO_AUTH_TOKEN');
const twilioFromNumber = defineSecret('TWILIO_FROM_NUMBER');
const twilioStatusCallbackUrl = defineSecret('TWILIO_STATUS_CALLBACK_URL');

/**
 * Fires on any write to an emergencyEvents/{eventId} doc, but only actually dispatches on the
 * transition INTO status "ACTIVE" — not on every subsequent write to the same doc (e.g. when
 * resolved_at is later set) and not if it was already ACTIVE before this write.
 */
export const onFamilyAlertDispatch = onDocumentWritten(
  {
    document: 'emergencyEvents/{eventId}',
    secrets: [twilioAccountSid, twilioAuthToken, twilioFromNumber, twilioStatusCallbackUrl],
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!after || after.status !== 'ACTIVE' || before?.status === 'ACTIVE') {
      return;
    }

    await dispatchFamilyAlerts({
      db: getFirestore(),
      messaging: getMessaging(),
      smsGateway: createTwilioSmsGateway({
        accountSid: twilioAccountSid.value(),
        authToken: twilioAuthToken.value(),
        fromNumber: twilioFromNumber.value(),
        statusCallbackBaseUrl: twilioStatusCallbackUrl.value(),
      }),
      eventId: event.params.eventId,
      eventData: after,
    });
  },
);

/** Called by a Safety Circle member's own device once it has actually received the push
 *  (LigayaMessagingService.onMessageReceived, core-notifications). */
export const confirmNotificationDeliveryCallable = onCall(async (request) => {
  const callerUserId = request.auth?.uid;
  if (!callerUserId) {
    throw new HttpsError('unauthenticated', 'Sign-in required.');
  }

  const { eventId, notificationEventId } = request.data ?? {};
  if (!eventId || !notificationEventId) {
    throw new HttpsError('invalid-argument', 'eventId and notificationEventId are required.');
  }

  try {
    await confirmNotificationDelivery({ db: getFirestore(), eventId, notificationEventId, callerUserId });
  } catch (error) {
    throw new HttpsError('permission-denied', String(error?.message ?? error));
  }

  return { confirmed: true };
});

/**
 * Section 18/27's family emergency data contract (Step 20): the only way a client reaches an
 * emergency event's family-facing view — there is no Firestore rule letting a non-owner read
 * emergencyEvents/locationEvents directly, specifically so a permission-gated field can never
 * reach the client at all, not just be hidden by it.
 */
export const getFamilyEmergencyView = onCall(async (request) => {
  const callerUserId = request.auth?.uid;
  if (!callerUserId) {
    throw new HttpsError('unauthenticated', 'Sign-in required.');
  }

  const { eventId } = request.data ?? {};
  if (!eventId) {
    throw new HttpsError('invalid-argument', 'eventId is required.');
  }

  try {
    return await computeFamilyEmergencyView({ db: getFirestore(), eventId, callerUserId });
  } catch (error) {
    throw new HttpsError('permission-denied', String(error?.message ?? error));
  }
});

/**
 * Twilio's delivery-receipt webhook (this step's own acceptance criterion). A public,
 * unauthenticated URL by nature (Twilio can't do Firebase Auth) — isValidTwilioSignature is what
 * stands in for authorization here, not Firestore rules or request.auth.
 */
export const twilioStatusCallback = onRequest(
  { secrets: [twilioAuthToken] },
  async (req, res) => {
    const fullUrl = `https://${req.get('host')}${req.originalUrl}`;
    const signatureValid = isValidTwilioSignature({
      authToken: twilioAuthToken.value(),
      url: fullUrl,
      params: req.body ?? {},
      signatureHeader: req.get('X-Twilio-Signature'),
    });
    if (!signatureValid) {
      res.status(403).send('invalid signature');
      return;
    }

    const { eventId, notificationEventId } = req.query;
    const deliveryStatus = req.body?.MessageStatus;
    if (!eventId || !notificationEventId || !deliveryStatus) {
      res.status(400).send('missing required fields');
      return;
    }

    const result = await handleSmsDeliveryCallback({
      db: getFirestore(),
      eventId: String(eventId),
      notificationEventId: String(notificationEventId),
      deliveryStatus: String(deliveryStatus),
    });
    res.status(result.updated ? 200 : 202).send(JSON.stringify(result));
  },
);
