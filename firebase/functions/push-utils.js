'use strict';
const crypto = require('node:crypto');

/** Hints only: the Firestore listener owns message synchronization. A push is
 * best-effort and intentionally carries NO chat text, SDP or identity data.
 */
function androidTransport(type, threadId) {
  if (!['new_message', 'group_message', 'group_invite', 'incoming_call'].includes(type) ||
      typeof threadId !== 'string' || threadId.length < 1 || threadId.length > 265) {
    throw new TypeError('Invalid push target');
  }
  const key = crypto.createHash('sha256').update(`${type}:${threadId}`).digest('hex').slice(0, 32);
  return {
    priority: type === 'new_message' || type === 'incoming_call' ? 'high' : 'normal',
    ttl: type === 'incoming_call' ? 60_000 : 15 * 60_000,
    collapseKey: `aaspass_${key}`
  };
}

module.exports = { androidTransport };
