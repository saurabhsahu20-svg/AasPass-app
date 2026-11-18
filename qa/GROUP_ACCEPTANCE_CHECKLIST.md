# AasPass v0.3 — Groups / Study acceptance plan

## Environment prerequisites

- [ ] `app/google-services.json` in place and matches registered package.
- [ ] Firebase Authentication (Email/Password), Firestore, FCM, and Cloud Functions v2 active.
- [ ] Deploy Firestore rules and Cloud Functions from the **same** v0.3 ZIP.
- [ ] Run Android Studio Gradle sync, debug build and unit tests.
- [ ] At least 2 devices, separate authenticated accounts and consent-based discovery.

## Permissions and membership

- [ ] Create group General as A; A is owner and admin, group only in A's group list.
- [ ] B not discoverable: A cannot invite B via username.
- [ ] B discoverable: A invites B; no membership before B accepts.
- [ ] B declines: remains outside group; cannot read group messages.
- [ ] B accepts: appears in both group lists; B can read and send.
- [ ] A can remove B; B then cannot read or send (test direct Firestore writes, not only UI).
- [ ] B can leave; A as owner cannot leave.
- [ ] Non-admin cannot invite or remove anyone (test callable requests directly).
- [ ] Forged direct Firestore writes to `groups`, `groupInvites`, `admins` and `members` are denied.
- [ ] Attempt to add 51st member is denied.
- [ ] Blocked sender cannot invite a blocked/hidden target.

## Messaging and study

- [ ] Group text A→B and B→A arrives on both devices.
- [ ] Server-confirmed Sent appears only after Firestore commit; offline message is Queued.
- [ ] Read after leave/removal returns permission denied.
- [ ] Group announcements can only be sent by admin (rule tests).
- [ ] Group message preview updates after send and never leaks into push payload.
- [ ] Invite and group-message push notifications arrive while app is backgrounded.
- [ ] Firebase App Check, token invalidation, spam/rate limits, abuse escalation reviewed.

## Out of scope

Video/audio calls, PDF sharing, homework reminders, E2EE and account deletion are not implemented in v0.3. No production release until these areas are assessed and all checks pass.
