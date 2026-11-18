'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { safePreview, shouldReplaceLastMessage, isBlocked } = require('../message-utils');
const timestamp = n => ({ toMillis: () => n });

test('previews are bounded and deleted text stays hidden', () => {
  assert.equal(safePreview({ text: 'hello' }), 'hello');
  assert.equal(safePreview({ text: 'secret', deletedAt: timestamp(23) }), 'Message deleted');
  assert.equal(safePreview({ text: 'a'.repeat(150) }).length, 100);
});
test('out-of-order events do not replace newer previews', () => {
  assert.equal(shouldReplaceLastMessage(timestamp(200), timestamp(100)), false);
  assert.equal(shouldReplaceLastMessage(timestamp(100), timestamp(100)), true);
  assert.equal(shouldReplaceLastMessage(null, timestamp(100)), true);
  assert.equal(shouldReplaceLastMessage(timestamp(100), null), false);
});
test('block in either direction prevents notification', () => {
  assert.equal(isBlocked('a', 'b', { blockedUids: ['b'] }, {}), true);
  assert.equal(isBlocked('a', 'b', {}, { blockedUids: ['a'] }), true);
  assert.equal(isBlocked('a', 'b', {}, {}), false);
});
