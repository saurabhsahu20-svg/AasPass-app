'use strict';
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { getAuth } = require('firebase-admin/auth');
const { getStorage } = require('firebase-admin/storage');
const { FieldValue } = require('firebase-admin/firestore');
const { recentlyAuthenticated } = require('./account-utils');

// Sensitive deletion is intentionally an authenticated server action, never
// triggered by deleting a client profile document. The client must reauthenticate.
module.exports = db => ({
  deleteMyAccount: onCall({ region: 'asia-south1', timeoutSeconds: 540,
    memory: '1GiB', maxInstances: 5 }, async req => {
    const uid = req.auth?.uid;
    if (!uid) throw new HttpsError('unauthenticated', 'Sign in again.');
    if (!recentlyAuthenticated(req.auth?.token?.auth_time)) {
      throw new HttpsError('failed-precondition', 'Sign in again before deleting your account.');
    }
    if (req.data?.confirmation !== 'DELETE MY AASPASS ACCOUNT') {
      throw new HttpsError('invalid-argument', 'Confirm permanent account deletion.');
    }
    const bucket = getStorage().bucket();
    async function removePrefix(prefix) {
      // Server-only deletion. Files are never made public for cleanup.
      await bucket.deleteFiles({ prefix, force: true });
    }
    try {
      const [ownedGroups, memberGroups, conversations, calls, incoming, sent, reports] = await Promise.all([
        db.collection('groups').where('ownerId', '==', uid).get(),
        db.collection('groups').where('members', 'array-contains', uid).get(),
        db.collection('conversations').where('participants', 'array-contains', uid).get(),
        db.collection('calls').where('participants', 'array-contains', uid).get(),
        db.collection('groupInvites').where('toUid', '==', uid).get(),
        db.collection('groupInvites').where('fromUid', '==', uid).get(),
        db.collection('reports').where('reporterUid', '==', uid).get()
      ]);
      for (const group of ownedGroups.docs) {
        // Members lose access to owner-deleted groups; warn user in UI.
        const invites = await db.collection('groupInvites').where('groupId', '==', group.id).get();
        for (const invite of invites.docs) await invite.ref.delete();
        await db.recursiveDelete(group.ref);
        await removePrefix(`media/group/${group.id}/`);
      }
      const ownedIds = new Set(ownedGroups.docs.map(d => d.id));
      for (const group of memberGroups.docs) {
        if (ownedIds.has(group.id)) continue;
        await group.ref.update({
          members: FieldValue.arrayRemove(uid),
          admins: FieldValue.arrayRemove(uid),
          [`memberNames.${uid}`]: FieldValue.delete()
        });
      }
      // Delete every message authored by this account in remaining groups,
      // including groups the user left in the past.
      let reachedLastPage = false;
      for (let page = 0; page < 100; page++) {
        const authored = await db.collectionGroup('messages')
          .where('senderId', '==', uid).limit(300).get();
        if (authored.empty) { reachedLastPage = true; break; }
        for (const doc of authored.docs) {
          const path = doc.get('mediaPath');
          if (typeof path === 'string' && path.startsWith('media/')) {
            await bucket.file(path).delete({ ignoreNotFound: true });
          }
          await doc.ref.delete();
        }
        if (authored.size < 300) { reachedLastPage = true; break; }
      }
      // Never falsely return success after a bounded cleanup: leave the account
      // authenticated so an operator/user can retry instead of silently skipping data.
      if (!reachedLastPage) {
        const remaining = await db.collectionGroup('messages')
          .where('senderId', '==', uid).limit(1).get();
        if (!remaining.empty) throw new Error('Account requires a paginated deletion job');
      }
      for (const chat of conversations.docs) {
        await db.recursiveDelete(chat.ref);
        await removePrefix(`media/direct/${chat.id}/`);
      }
      for (const call of calls.docs) await db.recursiveDelete(call.ref);
      for (const invite of [...incoming.docs, ...sent.docs]) await invite.ref.delete();
      for (const report of reports.docs) await report.ref.delete();
      await db.recursiveDelete(db.collection('pushTokens').doc(uid));
      const userRef = db.collection('users').doc(uid);
      const profile = await userRef.get();
      const handle = profile.get('username');
      if (typeof handle === 'string' && handle.length) {
        await db.collection('usernames').doc(handle).delete();
      }
      await userRef.delete();
      await db.collection('callRateLimits').doc(uid).delete();
      // Other users' safety reports about this account are retained for moderation.
      await getAuth().deleteUser(uid);
      return { deleted: true };
    } catch (error) {
      console.error('Account deletion needs retry', { uid, error: error?.message });
      throw new HttpsError('internal',
        'Account deletion was not fully completed. Reauthenticate and retry or contact support.');
    }
  })
});
