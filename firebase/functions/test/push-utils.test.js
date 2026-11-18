'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { androidTransport } = require('../push-utils');

test('direct message push is time-sensitive and collapses by thread', () => {
  const a = androidTransport('new_message', 'chat_001');
  assert.equal(a.priority, 'high');
  assert.equal(a.ttl, 900000);
  assert.deepEqual(a, androidTransport('new_message', 'chat_001'));
  assert.notEqual(a.collapseKey, androidTransport('new_message', 'chat_002').collapseKey);
});

test('call invites expire quickly and use high priority', () => {
  const a = androidTransport('incoming_call', 'call123');
  assert.equal(a.priority, 'high');
  assert.equal(a.ttl, 60000);
});

test('group notifications use efficient normal priority', () => {
  assert.equal(androidTransport('group_message', 'group123').priority, 'normal');
  assert.equal(androidTransport('group_invite', 'invite123').priority, 'normal');
});

test('reject malformed notification types and targets', () => {
  assert.throws(() => androidTransport('unknown', 'x'));
  assert.throws(() => androidTransport('new_message', ''));
  assert.throws(() => androidTransport('new_message', 'X'.repeat(266)));
});
