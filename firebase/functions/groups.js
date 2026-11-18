'use strict';

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { FieldValue } = require('firebase-admin/firestore');
const { validGroupName, validGroupKind,
  normalizeHandle, validHandle, canInvite, canJoin } = require('./group-utils');

const REGION = 'asia-south1';
const callableOpts = { region: REGION, maxInstances: 20 };
function requireUser(req) {
  if (!req.auth?.uid) throw new HttpsError('unauthenticated', 'Sign in to AasPass first.');
  return req.auth.uid;
}
function textInput(obj, name) {
  return typeof obj?.[name] === 'string' ? obj[name].trim() : '';
}
function validDocId(id) { return /^[A-Za-z0-9]{20}$/.test(id); }

module.exports = db => {
  const createGroup = onCall(callableOpts, async req => {
    const uid = requireUser(req);
    const name = textInput(req.data, 'name');
    const kind = textInput(req.data, 'kind');
    if (!validGroupName(name) || !validGroupKind(kind)) {
      throw new HttpsError('invalid-argument', 'Provide a group name (3–60 characters) and type.');
    }
    const ownerSnap = await db.collection('users').doc(uid).get();
    if (!ownerSnap.exists) throw new HttpsError('failed-precondition', 'Complete your profile first.');
    const ownerName = ownerSnap.get('displayName');
    if (typeof ownerName !== 'string' || !ownerName.trim()) {
      throw new HttpsError('failed-precondition', 'Invalid profile display name.');
    }
    const groupRef = db.collection('groups').doc();
    await groupRef.create({
      name, kind, ownerId: uid, admins: [uid], members: [uid], memberNames: { [uid]: ownerName },
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      lastMessage: '', lastAt: FieldValue.serverTimestamp()
    });
    return { groupId: groupRef.id };
  });

  const inviteToGroup = onCall(callableOpts, async req => {
    const uid = requireUser(req);
    const groupId = textInput(req.data, 'groupId');
    const username = normalizeHandle(req.data?.username);
    if (!validDocId(groupId) || !validHandle(username)) {
      throw new HttpsError('invalid-argument', 'Enter a valid group and exact username.');
    }
    const groupRef = db.collection('groups').doc(groupId);
    const handleRef = db.collection('usernames').doc(username);
    const handleSnap = await handleRef.get();
    if (!handleSnap.exists || handleSnap.get('discoverable') !== true) {
      throw new HttpsError('not-found', 'User not found or not discoverable.');
    }
    const targetUid = handleSnap.get('uid');
    if (typeof targetUid !== 'string' || targetUid === uid) {
      throw new HttpsError('invalid-argument', 'Choose another user.');
    }
    const targetRef = db.collection('users').doc(targetUid);
    const inviteRef = db.collection('groupInvites').doc(`${groupId}_${targetUid}`);
    await db.runTransaction(async tx => {
      const [groupSnap, targetSnap, senderSnap, inviteSnap] = await Promise.all([
        tx.get(groupRef), tx.get(targetRef),
        tx.get(db.collection('users').doc(uid)), tx.get(inviteRef)
      ]);
      if (!groupSnap.exists || !senderSnap.exists || !canInvite(
        groupSnap.data(), uid, targetUid, senderSnap.data(), targetSnap.data()
      )) throw new HttpsError('permission-denied', 'Invitation not allowed by group or privacy settings.');
      if (inviteSnap.exists) {
        throw new HttpsError('already-exists', 'This user was previously invited to this group.');
      }
      const group = groupSnap.data();
      tx.create(inviteRef, {
        groupId, groupName: group.name, groupKind: group.kind,
        fromUid: uid, toUid: targetUid, status: 'pending',
        createdAt: FieldValue.serverTimestamp()
      });
    });
    return { invited: true };
  });

  const respondToGroupInvite = onCall(callableOpts, async req => {
    const uid = requireUser(req);
    const inviteId = textInput(req.data, 'inviteId');
    const accept = req.data?.accept;
    if (!/^[A-Za-z0-9]{20}_[A-Za-z0-9_-]{4,128}$/.test(inviteId) || typeof accept !== 'boolean') {
      throw new HttpsError('invalid-argument', 'Invalid group invitation response.');
    }
    const inviteRef = db.collection('groupInvites').doc(inviteId);
    await db.runTransaction(async tx => {
      const inviteSnap = await tx.get(inviteRef);
      if (!inviteSnap.exists || inviteSnap.get('toUid') !== uid || inviteSnap.get('status') !== 'pending') {
        throw new HttpsError('permission-denied', 'No pending invitation for your account.');
      }
      const invite = inviteSnap.data();
      const groupRef = db.collection('groups').doc(invite.groupId);
      if (!accept) {
        tx.update(inviteRef, { status: 'declined', respondedAt: FieldValue.serverTimestamp() });
        return;
      }
      const [groupSnap, profileSnap] = await Promise.all([
        tx.get(groupRef), tx.get(db.collection('users').doc(uid))
      ]);
      if (!groupSnap.exists || !profileSnap.exists || !canJoin(groupSnap.data(), invite, uid)) {
        throw new HttpsError('failed-precondition', 'Group is unavailable or full.');
      }
      const displayName = profileSnap.get('displayName');
      if (typeof displayName !== 'string' || !displayName.trim()) {
        throw new HttpsError('failed-precondition', 'Complete your profile first.');
      }
      tx.update(groupRef, {
        members: FieldValue.arrayUnion(uid),
        [`memberNames.${uid}`]: displayName,
        updatedAt: FieldValue.serverTimestamp()
      });
      tx.update(inviteRef, { status: 'accepted', respondedAt: FieldValue.serverTimestamp() });
    });
    return { accepted: accept };
  });

  const leaveGroup = onCall(callableOpts, async req => {
    const uid = requireUser(req);
    const groupId = textInput(req.data, 'groupId');
    if (!validDocId(groupId)) throw new HttpsError('invalid-argument', 'Invalid group.');
    const groupRef = db.collection('groups').doc(groupId);
    await db.runTransaction(async tx => {
      const snap = await tx.get(groupRef);
      if (!snap.exists || !Array.isArray(snap.get('members')) || !snap.get('members').includes(uid)) {
        throw new HttpsError('permission-denied', 'Not a member of this group.');
      }
      if (snap.get('ownerId') === uid) {
        throw new HttpsError('failed-precondition', 'Group owner cannot leave.');
      }
      tx.update(groupRef, {
        members: FieldValue.arrayRemove(uid),
        admins: FieldValue.arrayRemove(uid),
        [`memberNames.${uid}`]: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp()
      });
    });
    return { left: true };
  });

  const removeGroupMember = onCall(callableOpts, async req => {
    const uid = requireUser(req);
    const groupId = textInput(req.data, 'groupId');
    const targetUid = textInput(req.data, 'targetUid');
    if (!validDocId(groupId) || !/^[A-Za-z0-9_-]{4,128}$/.test(targetUid)) {
      throw new HttpsError('invalid-argument', 'Invalid group member.');
    }
    const groupRef = db.collection('groups').doc(groupId);
    await db.runTransaction(async tx => {
      const snap = await tx.get(groupRef);
      const group = snap.data();
      if (!snap.exists || !group.admins?.includes(uid) ||
          !group.members?.includes(targetUid) || targetUid === group.ownerId || targetUid === uid) {
        throw new HttpsError('permission-denied', 'You cannot remove this group member.');
      }
      tx.update(groupRef, {
        members: FieldValue.arrayRemove(targetUid),
        admins: FieldValue.arrayRemove(targetUid),
        [`memberNames.${targetUid}`]: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp()
      });
    });
    return { removed: true };
  });

  return { createGroup, inviteToGroup, respondToGroupInvite, leaveGroup, removeGroupMember };
};
