'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { validGroupName, validGroupKind, normalizeHandle, validHandle,
  canInvite, canJoin, MAX_GROUP_MEMBERS } = require('../group-utils');
const base = { members: ['owner'], admins: ['owner'] };
const p = { blockedUids: [], discoverable: true };

test('group names and types are constrained', () => {
  assert.equal(validGroupName('Study Circle'), true);
  assert.equal(validGroupName('a'), false);
  assert.equal(validGroupName('a'.repeat(61)), false);
  assert.equal(validGroupKind('study'), true);
  assert.equal(validGroupKind('direct'), false);
});
test('username canonicalization and validation', () => {
  assert.equal(normalizeHandle('  STUDENT_12 '), 'student_12');
  assert.equal(validHandle('Student_12'), true);
  assert.equal(validHandle('wrong username'), false);
});
test('only admin may invite, never a blocked or hidden target', () => {
  assert.equal(canInvite(base, 'owner', 'student', p, p), true);
  assert.equal(canInvite(base, 'student2', 'student', p, p), false);
  assert.equal(canInvite(base, 'owner', 'student', p, {...p, discoverable:false}), false);
  assert.equal(canInvite(base, 'owner', 'student', p, {...p, blockedUids:['owner']}), false);
  assert.equal(canInvite(base, 'owner', 'student', {blockedUids:['student']}, p), false);
  assert.equal(canInvite({...base, members:['owner','student']}, 'owner', 'student', p, p), false);
});
test('50 member limit prevents invitations and acceptance', () => {
  const full = {...base, members: ['owner', ...Array.from({length: MAX_GROUP_MEMBERS-1}, (_,i)=>`u${i}`)]};
  assert.equal(canInvite(full, 'owner', 'new', p, p), false);
  assert.equal(canJoin(full, {toUid:'new', fromUid:'owner', status:'pending'}, 'new'), false);
});
test('acceptance requires recipient, pending invite, and still-admin inviter', () => {
  const invite = { toUid:'student', fromUid:'owner', status:'pending' };
  assert.equal(canJoin(base, invite, 'student'), true);
  assert.equal(canJoin(base, invite, 'other'), false);
  assert.equal(canJoin(base, {...invite, status:'declined'}, 'student'), false);
  assert.equal(canJoin({...base, admins:[]}, invite, 'student'), false);
});
