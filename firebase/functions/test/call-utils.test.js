'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { canStartCall, canRespond, canEnd, validCallId } = require('../call-utils');
test('calling requires accepted two-person chat and unblocked profiles', () => {
  const chat = { status: 'accepted', participants: ['u1', 'u2'] };
  assert.ok(canStartCall(chat, {}, {}, 'u1', 'u2'));
  assert.ok(!canStartCall(chat, {}, { blockedUids: ['u1'] }, 'u1', 'u2'));
  assert.ok(!canStartCall({ ...chat, status: 'requested' }, {}, {}, 'u1', 'u2'));
  assert.ok(!canStartCall(chat, {}, {}, 'outsider', 'u2'));
});
test('only receiver answers a ringing non-expired call', () => {
  const call = { callerUid: 'u1', calleeUid: 'u2', status: 'ringing',
    expiresAt: { toMillis: () => 5_000 } };
  assert.equal(canRespond(call, 'u2', 4999), true);
  assert.equal(canRespond(call, 'u1', 4999), false);
  assert.equal(canRespond(call, 'u2', 5000), false);
});
test('only participants can end and id validates', () => {
  assert.ok(validCallId('0123456789abcdefghij'));
  assert.ok(!validCallId('../'));
  assert.equal(canEnd({ callerUid: 'u1', calleeUid: 'u2', status: 'accepted' }, 'u2'), true);
  assert.equal(canEnd({ callerUid: 'u1', calleeUid: 'u2', status: 'ended' }, 'u2'), false);
});
