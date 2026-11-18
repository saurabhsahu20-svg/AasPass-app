'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { MAX_BYTES, cleanName, objectPath, attachmentKind,
  canPublishDirect, canPublishGroup } = require('../media-utils');
test('attachment checks reject unsupported content and oversized files', () => {
  assert.equal(attachmentKind('application/pdf', MAX_BYTES, 'notes.pdf'), 'pdf');
  assert.equal(attachmentKind('application/x-msdownload', 1, 'app.exe'), null);
  assert.equal(attachmentKind('image/jpeg', MAX_BYTES + 1, 'photo.jpg'), null);
  assert.equal(attachmentKind('image/png', 0, 'photo.png'), null);
  assert.equal(attachmentKind('image/png', 4, '../'), null);
});
test('storage path rejects traversal and unauthorized shape', () => {
  assert.equal(objectPath('direct', 'userA1234_userB1234', 'user_1234', '0123456789abcdefghij'),
    'media/direct/userA1234_userB1234/user_1234/0123456789abcdefghij');
  assert.equal(objectPath('direct', '../', 'u', 'bad'), null);
  assert.equal(objectPath('other', 'userA1234_userB1234', 'user_1234', '0123456789abcdefghij'), null);
  assert.equal(cleanName('a/b\\c?.pdf'), 'a_b_c_.pdf');
});
test('membership and blocking checked for publishing', () => {
  const chat = { status: 'accepted', participants: ['a', 'b'] };
  assert.equal(canPublishDirect(chat, 'a', {}, {}), true);
  assert.equal(canPublishDirect(chat, 'z', {}, {}), false);
  assert.equal(canPublishDirect(chat, 'a', {}, { blockedUids: ['a'] }), false);
  assert.equal(canPublishGroup({ members: ['a'] }, 'a'), true);
  assert.equal(canPublishGroup({ members: ['a'] }, 'b'), false);
});
