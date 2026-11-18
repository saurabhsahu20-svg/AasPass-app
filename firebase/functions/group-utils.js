'use strict';

const MAX_GROUP_MEMBERS = 50;
const validGroupName = name => typeof name === 'string' && name.trim().length >= 3 &&
  name.trim().length <= 60;
const validGroupKind = kind => kind === 'general' || kind === 'study';
const normalizeHandle = handle => typeof handle === 'string' ? handle.trim().toLowerCase() : '';
const validHandle = handle => /^[a-z0-9_]{3,20}$/.test(normalizeHandle(handle));
const canInvite = (group, senderUid, recipientUid, sender, recipient) => Boolean(
  group && Array.isArray(group.members) && Array.isArray(group.admins) &&
  group.members.includes(senderUid) && group.admins.includes(senderUid) &&
  group.members.length < MAX_GROUP_MEMBERS && !group.members.includes(recipientUid) &&
  recipient && recipient.discoverable === true &&
  !(Array.isArray(recipient.blockedUids) && recipient.blockedUids.includes(senderUid)) &&
  !(Array.isArray(sender?.blockedUids) && sender.blockedUids.includes(recipientUid))
);
const canJoin = (group, invite, uid) => Boolean(
  group && invite && invite.toUid === uid && invite.status === 'pending' &&
  Array.isArray(group.members) && group.members.length < MAX_GROUP_MEMBERS &&
  !group.members.includes(uid) && Array.isArray(group.admins) &&
  group.admins.includes(invite.fromUid)
);

module.exports = { MAX_GROUP_MEMBERS, validGroupName, validGroupKind,
  normalizeHandle, validHandle, canInvite, canJoin };
