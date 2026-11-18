# AasPass phase-2 developer test checklist

This checklist is **not evidence that device tests have passed**. Complete it on two phones with a dedicated Firebase project and deployed Firestore rules/functions.

- [ ] Fresh app install without `google-services.json` shows configuration-required screen (no fake login).
- [ ] Register two accounts and verify handles cannot be claimed by two users.
- [ ] Discovery defaults OFF; request to undiscoverable username fails safely.
- [ ] Enable discovery on recipient B; create chat request from A; B accepts.
- [ ] A and B exchange text messages; verify server acknowledgement labels Sent.
- [ ] Offline send shows Queued; reconnect then transitions to Sent.
- [ ] Read receipts default OFF on B; opening B's chat does not send a read marker.
- [ ] Enable Share read receipts on B, reopen chat; A sees Read on previously unread messages.
- [ ] Reply to another message, including when referenced message is outside the latest 100.
- [ ] Edit own message before the 15-minute limit; reject editing others' or expired messages.
- [ ] Soft-delete own message; both users see deleted placeholder; subsequent edit denied.
- [ ] When latest message is edited/deleted, conversation preview updates after Functions run.
- [ ] Rejected request cannot send messages; blocked user cannot send messages.
- [ ] Password reset email is sent to configured existing test account; reset completes.
- [ ] Foreground/background notification content stays generic; no private text leaked.
- [ ] Sign out removes this device's FCM registration before auth session ends.
- [ ] Font scaling, TalkBack labels, rotation, low-memory, small-screen, and dark mode.
- [ ] Firebase Emulator security-rule tests and audit with rules deployed; no cross-user read/write.
- [ ] Android Gradle build, instrumented tests, emulator and two-device smoke test.

Feature gates: voice/video calls, groups, study chat, attachments, E2EE, full deletion, and social moderation are not implemented and must remain clearly unavailable.
