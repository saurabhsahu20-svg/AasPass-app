'use strict';

const MAX_BYTES = 20 * 1024 * 1024;
const allowed = Object.freeze({
  'image/jpeg': 'image',
  'image/png': 'image',
  'image/webp': 'image',
  'video/mp4': 'video',
  'application/pdf': 'pdf',
  'audio/mp4': 'audio',
  'audio/aac': 'audio',
  'audio/mpeg': 'audio'
});
const validId = v => typeof v === 'string' && /^[A-Za-z0-9]{20}$/.test(v);
const cleanName = name => typeof name === 'string' ?
  name.replace(/[\x00-\x1f\x7f/\\<>:"|?*]/g, '_').trim().slice(0, 120) : '';
function attachmentKind(contentType, size, originalName) {
  if (typeof size !== 'number' || !Number.isFinite(size) || size <= 0 || size > MAX_BYTES) return null;
  if (typeof contentType !== 'string' || !(contentType in allowed)) return null;
  if (!cleanName(originalName) || !/[\p{L}\p{N}]/u.test(cleanName(originalName))) return null;
  return allowed[contentType];
}
function objectPath(scope, parentId, uid, assetId) {
  if (!['direct', 'group'].includes(scope) ||
      !(scope === 'direct' ? /^[A-Za-z0-9_-]{9,265}$/.test(parentId) : validId(parentId)) ||
      !/^[A-Za-z0-9_-]{4,128}$/.test(uid) || !validId(assetId)) return null;
  return `media/${scope}/${parentId}/${uid}/${assetId}`;
}
function canPublishDirect(chat, uid, sender, peer) {
  if (!chat || chat.status !== 'accepted' || !Array.isArray(chat.participants) ||
      chat.participants.length !== 2 || !chat.participants.includes(uid)) return false;
  const other = chat.participants.find(x => x !== uid);
  return !(sender?.blockedUids || []).includes(other) &&
    !(peer?.blockedUids || []).includes(uid);
}
function canPublishGroup(group, uid) {
  return !!(group && Array.isArray(group.members) && group.members.includes(uid));
}
module.exports = { MAX_BYTES, allowed, validId, cleanName, attachmentKind,
  objectPath, canPublishDirect, canPublishGroup };
