import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { isValidTwilioSignature } from '../twilio-sms-gateway.js';

const authToken = 'test-auth-token-123';
const url = 'https://example.com/twilioStatusCallback?eventId=event-1&notificationEventId=notif-1';
const params = { MessageStatus: 'delivered', MessageSid: 'SM123' };

function computeSignature({ authToken: token, url: signedUrl, params: signedParams }) {
  const sortedKeys = Object.keys(signedParams).sort();
  const data = sortedKeys.reduce((acc, key) => acc + key + signedParams[key], signedUrl);
  return crypto.createHmac('sha1', token).update(Buffer.from(data, 'utf-8')).digest('base64');
}

test('accepts a correctly computed signature', () => {
  const signature = computeSignature({ authToken, url, params });

  const result = isValidTwilioSignature({ authToken, url, params, signatureHeader: signature });

  assert.equal(result, true);
});

test('rejects a signature computed with the wrong auth token', () => {
  const signature = computeSignature({ authToken: 'wrong-token', url, params });

  const result = isValidTwilioSignature({ authToken, url, params, signatureHeader: signature });

  assert.equal(result, false);
});

test('rejects when the params were tampered with after signing', () => {
  const signature = computeSignature({ authToken, url, params });
  const tamperedParams = { ...params, MessageStatus: 'failed' };

  const result = isValidTwilioSignature({ authToken, url, params: tamperedParams, signatureHeader: signature });

  assert.equal(result, false);
});

test('rejects a missing signature header', () => {
  const result = isValidTwilioSignature({ authToken, url, params, signatureHeader: undefined });

  assert.equal(result, false);
});

test('rejects a signature of a different length without throwing', () => {
  const result = isValidTwilioSignature({ authToken, url, params, signatureHeader: 'short' });

  assert.equal(result, false);
});
