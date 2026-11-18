'use strict';
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { defineSecret, defineString } = require('firebase-functions/params');
const { Timestamp, FieldValue } = require('firebase-admin/firestore');
const crypto = require('node:crypto');
const { VALID_KINDS, validCallId, canStartCall, canRespond, canEnd } = require('./call-utils');
const opts = { region: 'asia-south1', maxInstances: 20 };
const TURN_SECRET = defineSecret('TURN_SHARED_SECRET');
const TURN_HOST = defineString('TURN_HOST', { default: '' });
function authenticated(req) {
  if (!req.auth?.uid) throw new HttpsError('unauthenticated', 'Sign in to AasPass.');
  return req.auth.uid;
}
module.exports = db => {
  const startCall = onCall(opts, async req => {
    const callerUid = authenticated(req);
    const { chatId, kind } = req.data || {};
    if (typeof chatId !== 'string' || !/^[A-Za-z0-9_-]{9,265}$/.test(chatId) ||
        !VALID_KINDS.has(kind)) throw new HttpsError('invalid-argument', 'Invalid call.');
    const chatRef = db.collection('conversations').doc(chatId);
    const chatSnap = await chatRef.get();
    if (!chatSnap.exists) throw new HttpsError('not-found', 'Conversation not found.');
    const chat = chatSnap.data();
    const calleeUid = chat.participants?.find(id => id !== callerUid);
    if (!calleeUid) throw new HttpsError('permission-denied', 'Not a participant.');
    const [caller, callee] = await Promise.all([
      db.collection('users').doc(callerUid).get(),
      db.collection('users').doc(calleeUid).get()
    ]);
    if (!canStartCall(chat, caller.data(), callee.data(), callerUid, calleeUid)) {
      throw new HttpsError('permission-denied', 'Calling is not allowed for this conversation.');
    }
    const callRef = db.collection('calls').doc();
    const rateRef = db.collection('callRateLimits').doc(callerUid);
    await db.runTransaction(async tx => {
      const rate = await tx.get(rateRef);
      const nowMs = Date.now();
      const nextAllowed = rate.get('nextAllowedAt')?.toMillis?.() || 0;
      const windowStart = rate.get('hourWindowStart')?.toMillis?.() || 0;
      const count = nowMs - windowStart > 3_600_000 ? 0 : (rate.get('hourCount') || 0);
      if (nowMs < nextAllowed || count >= 8)
        throw new HttpsError('resource-exhausted', 'Too many calls. Please try again later.');
      tx.set(rateRef, {
        nextAllowedAt: Timestamp.fromMillis(nowMs + 20_000),
        hourWindowStart: Timestamp.fromMillis(count ? windowStart : nowMs),
        hourCount: count + 1
      });
      tx.create(callRef, {
        participants: [callerUid, calleeUid], callerUid, calleeUid,
        callerName: caller.get('displayName') || 'AasPass user',
        calleeName: callee.get('displayName') || 'AasPass user',
        chatId, kind, status: 'ringing',
        createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
        expiresAt: Timestamp.fromMillis(nowMs + 75_000)
      });
    });
    return { callId: callRef.id };
  });

  const answerCall = onCall(opts, async req => {
    const uid = authenticated(req);
    const { callId, accept } = req.data || {};
    if (!validCallId(callId) || typeof accept !== 'boolean') {
      throw new HttpsError('invalid-argument', 'Invalid call response.');
    }
    const ref = db.collection('calls').doc(callId);
    await db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      if (!snap.exists || !canRespond(snap.data(), uid, Date.now())) {
        throw new HttpsError('failed-precondition', 'Call has expired or was answered.');
      }
      tx.update(ref, { status: accept ? 'accepted' : 'declined',
        updatedAt: FieldValue.serverTimestamp() });
    });
    return { accepted: accept };
  });

  const endCall = onCall(opts, async req => {
    const uid = authenticated(req);
    const callId = req.data?.callId;
    if (!validCallId(callId)) throw new HttpsError('invalid-argument', 'Invalid call.');
    const ref = db.collection('calls').doc(callId);
    await db.runTransaction(async tx => {
      const snap = await tx.get(ref);
      if (!snap.exists || !snap.get('participants')?.includes(uid)) {
        throw new HttpsError('permission-denied', 'Not a call participant.');
      }
      if (!canEnd(snap.data(), uid)) return; // Idempotent end.
      tx.update(ref, { status: 'ended', updatedAt: FieldValue.serverTimestamp() });
    });
    return { ended: true };
  });

  // Generate short-lived TURN credentials on the backend (coturn --use-auth-secret).
  // A valid Firebase user AND a participant in an active call are required.
  const getCallIceServers = onCall({ ...opts, secrets: [TURN_SECRET] }, async req => {
    const uid = authenticated(req);
    const callId = req.data?.callId;
    if (!validCallId(callId)) throw new HttpsError('invalid-argument', 'Invalid call.');
    const call = await db.collection('calls').doc(callId).get();
    if (!call.exists || !call.get('participants')?.includes(uid) ||
        !['ringing', 'accepted'].includes(call.get('status')) ||
        (call.get('status') === 'ringing' && call.get('expiresAt')?.toMillis?.() < Date.now())) {
      throw new HttpsError('permission-denied', 'Call is unavailable.');
    }
    const host = TURN_HOST.value().trim();
    const secret = TURN_SECRET.value();
    if (!host || !secret || !/^[a-z0-9.-]{3,253}$/i.test(host)) {
      throw new HttpsError('failed-precondition', 'TURN server is not configured.');
    }
    const expiry = Math.floor(Date.now() / 1000) + 600;
    const username = `${expiry}:${uid}`;
    const credential = crypto.createHmac('sha1', secret).update(username).digest('base64');
    return { iceServers: [{ urls: [`turn:${host}:3478?transport=udp`,
      `turns:${host}:5349?transport=tcp`], username, credential }] };
  });
  return { startCall, answerCall, endCall, getCallIceServers };
};
