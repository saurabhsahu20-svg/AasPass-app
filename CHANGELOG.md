# Changelog

## 0.7.0-alpha — read-receipt accuracy and safe media cache

- Read receipts now require explicit opt-in, the visible conversation in a RESUMED lifecycle state and the latest peer message actually present among rendered/visible message rows. Background Firestore updates no longer mark messages read.
- Prune app-owned cached attachments older than 48 hours, or past 80 MiB subject to a 15-minute safety window for active document viewers. Unrelated files are untouched.
- Add standalone eight-assertion Kotlin regression tests and Android JUnit tests for these behaviors.
- Add lifecycle-runtime-compose dependency for supported LocalLifecycleOwner integration.
- This source change has not undergone an Android SDK build or device tests; production publication remains blocked.


## 0.6.0-alpha — messaging correctness and group push fanout

- Fix message timeline merges so latest Firestore edits/deletions cannot be overridden by older paginated cache entries.
- Speed up reply preview lookup for long conversations via a precomputed map.
- Fix group screen stuck on indefinite spinner when the group is unavailable.
- Dispatch group push notifications with bounded parallelism (8 workers) and add deterministic utility tests.
- Remove duplicate `android` FCM options on group invitations.
- Add optional guarded GitHub CLI source-publication helper. No deployment or signed release performed.

## 0.5.0-alpha — notification reliability & UI responsiveness

- Routed valid notification taps directly to private chats, groups, calls and the appropriate group-invitation tab, including after sign-in. Malformed routes are rejected.
- Per-thread Android notifications now replace earlier duplicate alerts, not create an unlimited stack.
- Added time-bounded, collapse-keyed FCM hints. Direct chat and incoming call alerts use Android high priority when notifications are enabled; group alerts use normal priority to conserve battery. FCM is not guaranteed instantaneous.
- Stopped FCM retry suppression after a Firestore preview update: retries can re-send an unconfirmed push, with client-side coalescing. Notifications are at-least-once / best-effort.
- Added recent-chat query cap (75) and 'Load older conversations' paging with required Firestore composite index.
- Improved chat/group UI scrolling to avoid moving away from old messages when new content arrives; keep keyboard visible after sending.
- Added optional Android 13+ notification permission explanation after profile setup.
- Added JS notification transport tests and Kotlin navigation route tests.
- Kept all v0.4 media, group, study, WebRTC and security source. No public release was attempted without signed builds and real-device tests.

# Changelog

## 0.4.0-alpha — 2026-11-18

- Added private media attachments in direct and group chats: JPEG/PNG/WebP images, MP4 video, PDF and supported audio (20 MiB maximum); server-verifies media before publishing messages.
- Added scoped Firebase Storage rules, MIME/size/path restrictions and private FileProvider-based opening of downloads; no public download-token URL generation.
- Added 1:1 WebRTC voice/video call UI and session code, member-restricted Firestore SDP/ICE signaling, incoming-call FCM events, voice/video controls, call history and short-lived TURN credentials via callable backend.
- Added caller rate limits, call expiry scheduler, and client microphone/camera runtime permissions.
- Added recent-login-verified account deletion callable and Android confirmation screen, with explicit caveats for large account cleanup and remote history loss.
- Added pagination for older private and group messages, and offline-safe sign-out + FCM recipient-UID checks.
- Fixed attachment error-state declaration order in ChatScreen; tightened direct asset-ID rule and bounded account-deletion erasure to fail closed.
- Added JS utility tests, static verification, more complete CI, deployment guide and release gate checklist.
- **Not production verified**: Android build, Firebase Emulator, active WebRTC/TURN calls, media transfers and release signing remain untested here.

## 0.3.0-alpha — 2026-11-17

- Introduced private groups, Study groups, invitations and server-managed membership, group messaging and announcements.
- Added group FCM notifications and group authorization rules.

## 0.2.0-alpha

- Added replies, time-limited message edits, soft deletion, opt-in read receipts, password reset and private username lookup.

## v0.8.0-alpha — 18 November 2026

- Harden group FCM fanout so one unavailable recipient cannot stop attempts to notify other members.
- Remove permanently invalid recipient push tokens after group multicast; retain transiently failed tokens for retry.
- Four new backend tests; 25 total.
- Prepare dedicated local Git repository, safe publishing script and GitHub CI checklist.
- Full Android build, end-to-end tests and GitHub remote creation remain pending.
