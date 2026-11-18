'use strict';

function safePreview(message) {
  if (!message || message.deletedAt) return 'Message deleted';
  if (message.mediaPath && message.fileName) return 'Attachment: ' + message.fileName.slice(0, 90);
  return typeof message.text === 'string' ? message.text.slice(0, 100) : '';
}

function shouldReplaceLastMessage(previousTimestamp, incomingTimestamp) {
  if (!incomingTimestamp || typeof incomingTimestamp.toMillis !== 'function') return false;
  if (!previousTimestamp || typeof previousTimestamp.toMillis !== 'function') return true;
  return incomingTimestamp.toMillis() >= previousTimestamp.toMillis();
}

function isBlocked(senderUid, recipientUid, sender, recipient) {
  return (Array.isArray(sender?.blockedUids) && sender.blockedUids.includes(recipientUid)) ||
    (Array.isArray(recipient?.blockedUids) && recipient.blockedUids.includes(senderUid));
}

module.exports = { safePreview, shouldReplaceLastMessage, isBlocked };
