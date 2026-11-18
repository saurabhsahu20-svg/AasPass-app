# v0.6 Release Gate (not passed yet)

STOP: This ZIP is alpha **source code**, not a release artifact.

# AasPass release gates (must all pass before public distribution)

## Build, runtime, performance
- [ ] Android Gradle `:app:assembleDebug` completes using JDK 17, SDK 36 and configured Firebase.
- [ ] GitHub Actions passes on a dedicated AasPass repository; this repository does not yet exist in the connected account.
- [ ] Android `:app:testDebugUnitTest` and on-device `connectedDebugAndroidTest` pass.
- [ ] App start, onboarding and logout work without crashing after app restarts and process death.
- [ ] Scroll large message/group histories smoothly on low-end Android phones; measure cold start, jank, memory, battery, and ANRs with benchmarks rather than assuming 60 FPS.
- [ ] Test accessibility font scaling, screen readers, touch targets and both themes.

## Firebase authorization (use Emulator Suite)
- [ ] Unauthorized user cannot read another person's profile/private block list, direct chat, group details, messages or media.
- [ ] User who declines a chat request cannot receive messages; blocked peers cannot deliver new text/media/calls.
- [ ] Removed member or declined invite cannot see private group history or media.
- [ ] Non-admin cannot post announcements, change group admins or invite new members without authorized backend action.
- [ ] A non-participant cannot read/write SDP or ICE, obtain TURN credentials or create/accept a call.
- [ ] Large/disallowed MIME media rejected; other user's storage objects cannot be overwritten or listed.
- [ ] Confirm Firestore rules' cross-document reads, Storage rules' Firestore service-agent access, indexes and billing behavior.
- [ ] Run rate-limit abuse tests, notification spam and multi-account privacy audit.

## Functional integration (two or more real devices)
- [ ] Login, signup, account recovery, profile changes, discoverability toggles.
- [ ] Chat acceptance, offline queued messages, online updates, edit/delete/reply, optional read receipts, back-pagination.
- [ ] Group/Study create, invite/decline/accept, admin removal, leave, announcements, back-pagination.
- [ ] Photos, MP4, PDF and audio attachment upload/download; weak network retries and orphan cleanup.
- [ ] Voice/video call on same/different networks, speaker/Bluetooth behavior, mic/camera permissions, switch camera, reject/hangup, remote termination and missed calls.
- [ ] Android foreground/background/terminated push notification handling, per-thread coalescing, correct Chat/Group/Study deep links, spam controls, account-switch and logout security.
- [ ] Validate FCM high-priority quota/deprioritization, app notification permission, OS Doze delays and end-to-end delivery latency on multiple devices.
- [ ] Account deletion audit including storage files, direct-chat peer history impact, past-group messages and retries after partial failure.

## Production rollout (not yet satisfied)
- [ ] Secure and monitor TURN/coturn, secrets, rate limiting, metrics, fallbacks and observability.
- [ ] Add network recovery and delivery receipts, production-scale account erasure jobs, content/abuse moderation and a retention policy.
- [ ] Implement reviewed end-to-end encryption if advertised; currently **not E2EE**.
- [ ] Review child privacy and safety, incident process, privacy policy, local legal requirements and consent flows.
- [ ] Build signed release AAB/APK, test on many devices, staged rollout with crash reporting, verify store policy and maintenance plan.
- [ ] Create verified developer identity/Play Console listing and supply a reviewed privacy policy; do not publish an unsigned debug APK as a public release.
