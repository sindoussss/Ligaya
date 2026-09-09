import crypto from 'node:crypto';

/**
 * The real SMS gateway adapter — Twilio, the concrete resolution of Issue D's "cloud gateway"
 * choice (over Semaphore/Movider): a globally standard, well-documented REST API with built-in
 * delivery-receipt webhooks and free sandbox/test credentials, so a real Twilio account (still
 * required — see this step's report) doesn't also require real money to exercise in test mode.
 *
 * Requires a real Twilio account (Account SID, Auth Token, a purchased/verified sending number)
 * — an account action only the app's operator can take, same as GooglePlacesNearbySearchSource's
 * API key requirement (Step 17). Credentials are plain parameters, never hardcoded.
 */
export function createTwilioSmsGateway({ accountSid, authToken, fromNumber, statusCallbackBaseUrl }) {
  return {
    async send({ to, body, eventId, notificationEventId }) {
      const url = `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Messages.json`;
      const statusCallback = `${statusCallbackBaseUrl}?eventId=${encodeURIComponent(eventId)}&notificationEventId=${encodeURIComponent(notificationEventId)}`;
      const params = new URLSearchParams({ To: to, From: fromNumber, Body: body, StatusCallback: statusCallback });

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          Authorization: `Basic ${Buffer.from(`${accountSid}:${authToken}`).toString('base64')}`,
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString(),
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(`Twilio send failed (${response.status}): ${text}`);
      }

      const json = await response.json();
      return { messageId: json.sid };
    },
  };
}

/**
 * Verifies a webhook request genuinely came from Twilio (Twilio's own documented X-Twilio-
 * Signature scheme: HMAC-SHA1 of the full callback URL + sorted POST params, keyed by the Auth
 * Token). Without this check, twilioStatusCallback (index.js) is a public, unauthenticated URL —
 * anyone who found it could POST a fake "delivered" status and falsely mark a family member as
 * confirmed alerted. A safety-critical false-claims concern (section 23), not just routine input
 * validation.
 */
export function isValidTwilioSignature({ authToken, url, params, signatureHeader }) {
  if (!signatureHeader) {
    return false;
  }
  const sortedKeys = Object.keys(params).sort();
  const data = sortedKeys.reduce((acc, key) => acc + key + params[key], url);
  const expected = crypto.createHmac('sha1', authToken).update(Buffer.from(data, 'utf-8')).digest('base64');

  const expectedBuffer = Buffer.from(expected);
  const actualBuffer = Buffer.from(signatureHeader);
  if (expectedBuffer.length !== actualBuffer.length) {
    return false;
  }
  return crypto.timingSafeEqual(expectedBuffer, actualBuffer);
}
