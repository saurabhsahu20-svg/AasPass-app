'use strict';

const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { safePreview, shouldReplaceLastMessage, isBlocked } = require('./message-utils');
const { androidTransport } = require('./push-utils');
const { forEachBestEffort, invalidDeviceRefs } = require('./push-fanout');
initializeApp();

const db = getFirestore();
const REGION = 'asia-south1';
const MESSAGE_PATH = 'conversations/{chatId}/messages/{messageId}';

// Message content never enters FCM. The sender's identity is not exposed in notification payloads.
exports.onNewChatMessage = onDocumentCreated(
  { document: MESSAGE_PATH, region: REGION },
  async event => {
    const message = event.data?.data();
    if (!message || typeof message.text !== 'string' || typeof message.senderId !== 'string') return;
    if (!message.createdAt || typeof message.createdAt.toMillis !== 'function') return;
    const chatId = event.params.chatId;
    const chatRef = db.collection('conversations').doc(chatId);
    const result = await db.runTransaction(async tx => {
      // Read the latest message state too: it may already have been edited/deleted
      // before the creation trigger runs. All transaction reads precede writes.
      const msgRef = chatRef.collection('messages').doc(event.params.messageId);
      const chatSnap = await tx.get(chatRef);
      const liveMessage = await tx.get(msgRef);
      if (!chatSnap.exists || !liveMessage.exists) return null;
      const chat = chatSnap.data();
      if (!Array.isArray(chat.participants) || chat.participants.length !== 2 ||
          !chat.participants.includes(message.senderId) || chat.status !== 'accepted') return null;
      // A retry must still be able to re-send a failed push. Notifications are
      // at-least-once and clients coalesce them by conversation. Do not confuse
      // preview idempotence with push idempotence.
      if (chat.lastMessageId !== event.params.messageId &&
          shouldReplaceLastMessage(chat.lastAt, message.createdAt)) {
        tx.update(chatRef, {
          lastMessage: safePreview(liveMessage.data()),
          lastMessageId: event.params.messageId,
          lastAt: message.createdAt
        });
      }
      return { participants: chat.participants, senderId: message.senderId };
    });
    // Delayed events never overwrite a newer preview, but can still notify.
    if (!result) return;
    const recipients = result.participants.filter(uid => uid !== result.senderId);
    for (const uid of recipients) {
      const [senderSnap, recipientSnap] = await Promise.all([
        db.collection('users').doc(result.senderId).get(),
        db.collection('users').doc(uid).get()
      ]);
      if (!senderSnap.exists || !recipientSnap.exists ||
          isBlocked(result.senderId, uid, senderSnap.data(), recipientSnap.data())) continue;
      const devices = await db.collection('pushTokens').doc(uid).collection('devices').get();
      if (devices.empty) continue;
      const docs = devices.docs;
      const tokens = docs.map(d => d.get('token')).filter(x => typeof x === 'string' && x.length > 0);
      for (let offset = 0; offset < tokens.length; offset += 500) {
        const chunk = tokens.slice(offset, offset + 500);
        const response = await getMessaging().sendEachForMulticast({
          tokens: chunk,
          android: androidTransport('new_message', chatId),
          data: { type: 'new_message', chatId, toUid: uid }
        });
        const invalidCodes = new Set([
          'messaging/registration-token-not-registered', 'messaging/invalid-registration-token'
        ]);
        const removals = [];
        response.responses.forEach((item, index) => {
          if (!item.success && invalidCodes.has(item.error?.code)) {
            const doc = docs.find(d => d.get('token') === chunk[index]);
            if (doc) removals.push(doc.ref.delete());
          }
        });
        await Promise.all(removals);
      }
    }
  }
);

// Keep the conversation preview consistent when the most recent message is edited/deleted.
exports.onChatMessageChanged = onDocumentUpdated(
  { document: MESSAGE_PATH, region: REGION },
  async event => {
    if (!event.data?.after?.exists) return;
    const chatRef = db.collection('conversations').doc(event.params.chatId);
    const msgRef = chatRef.collection('messages').doc(event.params.messageId);
    await db.runTransaction(async tx => {
      // Read the live state so out-of-order edit/delete events cannot resurrect old text.
      const chatSnap = await tx.get(chatRef);
      const msgSnap = await tx.get(msgRef);
      if (!chatSnap.exists || !msgSnap.exists ||
          chatSnap.get('lastMessageId') !== event.params.messageId) return;
      tx.update(chatRef, { lastMessage: safePreview(msgSnap.data()) });
    });
  }
);

// Group membership and invitations must be authorized on the server.
Object.assign(exports, require('./groups')(db));

// Keep group list metadata in sync, without putting message bodies into push payloads.
exports.onNewGroupMessage = onDocumentCreated(
  { document: 'groups/{groupId}/messages/{messageId}', region: REGION },
  async event => {
    const message = event.data?.data();
    if (!message || typeof message.text !== 'string' ||
        typeof message.senderId !== 'string' || !message.createdAt) return;
    const groupRef = db.collection('groups').doc(event.params.groupId);
    const groupInfo = await db.runTransaction(async tx => {
      const snap = await tx.get(groupRef);
      if (!snap.exists) return null;
      const group = snap.data();
      if (!Array.isArray(group.members) || !group.members.includes(message.senderId)) return null;
      if (group.lastMessageId !== event.params.messageId &&
          shouldReplaceLastMessage(group.lastAt, message.createdAt)) {
        tx.update(groupRef, {
          lastMessage: message.mediaPath && message.fileName ? 'Attachment: ' + message.fileName.slice(0, 90)
            : message.kind === 'announcement' ? 'Announcement: ' + message.text.slice(0, 86)
            : message.text.slice(0, 100),
          lastAt: message.createdAt,
          lastMessageId: event.params.messageId
        });
      }
      return { members: group.members, senderId: message.senderId };
    });
    if (!groupInfo) return;
    // Deliver a group notification to recipients in bounded parallel batches.
    const delivery = await forEachBestEffort(
      groupInfo.members.filter(id => id !== groupInfo.senderId), 8, async uid => {
        const [profile, devices] = await Promise.all([
          db.collection('users').doc(uid).get(),
          db.collection('pushTokens').doc(uid).collection('devices').get()
        ]);
        if (profile.exists && Array.isArray(profile.get('blockedUids')) &&
            profile.get('blockedUids').includes(groupInfo.senderId)) return;
        const validDocs = devices.docs.filter(doc => {
          const token = doc.get('token');
          return typeof token === 'string' && token.length >= 20;
        });
        for (let offset = 0; offset < validDocs.length; offset += 500) {
          const batch = validDocs.slice(offset, offset + 500);
          const response = await getMessaging().sendEachForMulticast({
            tokens: batch.map(doc => doc.get('token')),
            android: androidTransport('group_message', event.params.groupId),
            data: { type: 'group_message', groupId: event.params.groupId, toUid: uid }
          });
          const stale = invalidDeviceRefs(batch, response.responses);
          await Promise.allSettled(stale.map(ref => ref.delete()));
        }
      });
    if (delivery.failed) console.warn('Group FCM delivery failed for recipients:', delivery.failed);
  }
);


exports.onGroupInviteCreated = onDocumentCreated(
  { document: 'groupInvites/{inviteId}', region: REGION },
  async event => {
    const invite = event.data?.data();
    if (!invite || invite.status !== 'pending' || typeof invite.toUid !== 'string') return;
    const devices = await db.collection('pushTokens').doc(invite.toUid).collection('devices').get();
    const tokens = devices.docs.map(d => d.get('token')).filter(Boolean);
    for (let offset = 0; offset < tokens.length; offset += 500) {
      await getMessaging().sendEachForMulticast({
        tokens: tokens.slice(offset, offset + 500),
        data: { type: 'group_invite', toUid: invite.toUid,
          groupKind: invite.groupKind === 'study' ? 'study' : 'general' },
        android: androidTransport('group_invite', event.params.inviteId)
      });
    }
  }
);

// Only verified, completed Storage uploads become chat messages.
Object.assign(exports, require('./media')(db));

// One-to-one authenticated call signaling and short-lived TURN credential issuance.
Object.assign(exports, require('./calls')(db));

// Call invitations contain no SDP, media keys, or personal contact information.
exports.onNewIncomingCall = onDocumentCreated(
  { document: 'calls/{callId}', region: REGION },
  async event => {
    const call = event.data?.data();
    if (!call || call.status !== 'ringing' || !['voice', 'video'].includes(call.kind)) return;
    const devices = await db.collection('pushTokens').doc(call.calleeUid).collection('devices').get();
    const tokens = devices.docs.map(d => d.get('token')).filter(x => typeof x === 'string' && x.length);
    for (let i = 0; i < tokens.length; i += 500) {
      await getMessaging().sendEachForMulticast({
        tokens: tokens.slice(i, i + 500),
        data: { type: 'incoming_call', callId: event.params.callId, toUid: call.calleeUid },
        android: androidTransport('incoming_call', event.params.callId)
      });
    }
  }
);

// Expires stale ringing calls; without it they remain as missed after app restarts.
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { Timestamp, FieldValue } = require('firebase-admin/firestore');
exports.expireRingingCalls = onSchedule(
  { schedule: 'every 2 minutes', region: REGION, timeZone: 'UTC' },
  async () => {
    const expired = await db.collection('calls').where('status', '==', 'ringing')
      .where('expiresAt', '<=', Timestamp.now()).limit(100).get();
    await Promise.all(expired.docs.map(doc => db.runTransaction(async tx => {
      const fresh = await tx.get(doc.ref);
      if (fresh.exists && fresh.get('status') === 'ringing' &&
          fresh.get('expiresAt')?.toMillis() <= Date.now()) {
        tx.update(doc.ref, { status: 'missed', updatedAt: FieldValue.serverTimestamp() });
      }
    })));
  }
);

// Server-led account deletion requires recent Firebase authentication.
Object.assign(exports, require('./account')(db));
