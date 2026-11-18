'use strict';
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { getStorage } = require('firebase-admin/storage');
const { FieldValue } = require('firebase-admin/firestore');
const { validId, cleanName, attachmentKind, objectPath,
  canPublishDirect, canPublishGroup } = require('./media-utils');
const options = { region: 'asia-south1', maxInstances: 20, enforceAppCheck: false };

/** The client uploads a private object, then this callable *verifies* the object
 * before publishing a Firestore message. A caller cannot forge media metadata.
 */
module.exports = db => {
  async function publish(req, scope) {
    const uid = req.auth?.uid;
    if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
    const parentId = req.data?.parentId;
    const assetId = req.data?.assetId;
    const fileName = cleanName(req.data?.fileName);
    const path = objectPath(scope, parentId, uid, assetId);
    if (!path || !fileName || !validId(assetId)) {
      throw new HttpsError('invalid-argument', 'Invalid attachment details.');
    }
    const parentRef = db.collection(scope === 'direct' ? 'conversations' : 'groups').doc(parentId);
    const parentSnap = await parentRef.get();
    if (!parentSnap.exists) throw new HttpsError('not-found', 'Conversation is unavailable.');
    const data = parentSnap.data();
    if (scope === 'direct') {
      const peerUid = data.participants?.find(id => id !== uid);
      if (!peerUid || !data.participants?.includes(uid)) {
        throw new HttpsError('permission-denied', 'Not a participant.');
      }
      const [sender, peer] = await Promise.all([
        db.collection('users').doc(uid).get(), db.collection('users').doc(peerUid).get()
      ]);
      if (!canPublishDirect(data, uid, sender.data(), peer.data())) {
        throw new HttpsError('permission-denied', 'Messaging is not allowed.');
      }
    } else if (!canPublishGroup(data, uid)) {
      throw new HttpsError('permission-denied', 'Not a group member.');
    }
    const file = getStorage().bucket().file(path);
    const [exists] = await file.exists();
    if (!exists) throw new HttpsError('failed-precondition', 'Upload was not completed.');
    const [metadata] = await file.getMetadata();
    const kind = attachmentKind(metadata.contentType, Number(metadata.size), fileName);
    if (!kind) throw new HttpsError('invalid-argument', 'File type or size not permitted.');
    const messageRef = parentRef.collection('messages').doc(assetId);
    try {
      await db.runTransaction(async tx => {
        const [current, existing] = await Promise.all([tx.get(parentRef), tx.get(messageRef)]);
        if (!current.exists || existing.exists) {
          throw new HttpsError('already-exists', 'This attachment was already published or the chat is unavailable.');
        }
        // Repeat membership check under the transaction to prevent remove-while-publishing.
        const parent = current.data();
        if ((scope === 'direct' && (parent.status !== 'accepted' || !parent.participants?.includes(uid))) ||
            (scope === 'group' && !parent.members?.includes(uid))) {
          throw new HttpsError('permission-denied', 'Your access has changed.');
        }
        const message = {
          senderId: uid, text: '', kind, fileName,
          mediaPath: path, contentType: metadata.contentType,
          fileSize: Number(metadata.size), createdAt: FieldValue.serverTimestamp()
        };
        if (scope === 'direct') message.replyToId = '';
        tx.create(messageRef, message);
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      throw new HttpsError('internal', 'Could not publish this attachment.');
    }
    return { messageId: assetId, kind };
  }
  return {
    publishDirectAttachment: onCall(options, req => publish(req, 'direct')),
    publishGroupAttachment: onCall(options, req => publish(req, 'group'))
  };
};
