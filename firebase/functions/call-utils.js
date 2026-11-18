'use strict';
const VALID_KINDS = new Set(['voice', 'video']);
const ACTIVE_STATUSES = new Set(['ringing', 'accepted']);
const validCallId = id => typeof id === 'string' && /^[A-Za-z0-9]{20}$/.test(id);
function canStartCall(chat, caller, callee, callerUid, calleeUid) {
  return chat?.status === 'accepted' && Array.isArray(chat.participants) &&
    chat.participants.length === 2 && chat.participants.includes(callerUid) &&
    chat.participants.includes(calleeUid) && callerUid !== calleeUid &&
    !!caller && !!callee &&
    !caller.blockedUids?.includes(calleeUid) &&
    !callee.blockedUids?.includes(callerUid);
}
function canRespond(call, uid, nowMs) {
  return call?.calleeUid === uid && call?.status === 'ringing' &&
    call.expiresAt?.toMillis?.() > nowMs;
}
function canEnd(call, uid) {
  return !!call && ACTIVE_STATUSES.has(call.status) &&
    (call.callerUid === uid || call.calleeUid === uid);
}
module.exports = { VALID_KINDS, ACTIVE_STATUSES, validCallId,
  canStartCall, canRespond, canEnd };
