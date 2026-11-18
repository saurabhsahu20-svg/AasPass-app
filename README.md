# MsgU — Smooth Messaging Preview (Android Studio)

**Version: 0.8.0-alpha | Source project, NOT a verified release APK.**

**Tagline:** Connect • Chat • Call • Learn. Kotlin + Jetpack Compose + Firebase + WebRTC. The green crystal AasPass logo from the prior project is preserved (`branding/official-logo-original.png` and `mipmap-*`). No third-party messaging UI was copied.

## Feature status: code implemented, not end-to-end verified

| Area | Included in source | Before claiming it works in production |
| --- | --- | --- |
| UI | 5 tabs, splash, original icon, Material 3 light/dark themes, conversation search, optional Android 13+ notification consent screen, reduced chat auto-scroll jumps | Android build and device/accessibility profiling |
| Authentication | Firebase email/password, password reset, unique usernames, profile, logout, password-reauthenticated deletion | Firebase Auth configuration and account deletion tests |
| Direct chats | Invite/accept, realtime text, reply, edit <=15 min, delete text, opt-in read status, offline Firestore queue, bounded 75-item recent conversation listener with back-pagination, older-message pagination | two-account messaging tests incl. network drop and account switching |
| Groups and Study | Private groups, invitations, acceptance/decline, member administration, group text, admin announcements, older-message pagination | deployed Functions, multi-device permission tests |
| Attachments | Images (JPEG/PNG/WebP), MP4 video, PDF, supported audio files up to 20 MiB, checked server publication, private download | Cloud Storage rules, actual upload/download tests |
| Voice/video | WebRTC 1:1 SDP/ICE signaling, incoming call alerts, call history, audio/video controls, expiring TURN credentials | TURN server + Firebase Functions + WebRTC testing on 2+ real devices and networks |
| Notifications | Data-only FCM, generic text, recipient-UID check after logout, validated chat/group/call deep navigation, group-invite tab routing, per-thread notification replacement, high-priority direct-message pushes | FCM latency and foreground/background/terminated delivery must be measured; high priority is not a delivery guarantee |
| Security | Private accounts, username opt-in, block/report direct users, Firestore and Storage permissions, caller auth, no public media download URLs | Emulator-based rules tests, abuse audit, threat modeling |

**Not implemented / not promised:** Verified delivery-to-device marks, E2EE, multi-device message synchronization beyond Firestore, native full-screen background Telecom calling, call recording, group voice/video calls, video PiP, document previews inside the app, Google Sign-In, scheduled homework reminders, comprehensive moderation dashboard, scalable account-erasure jobs, production accessibility/device certification, and real-world performance benchmarks. Messaging uses TLS/Firebase security, but **is not end-to-end encrypted**. Incoming calls rely on best-effort FCM and the app being able to run, not Android Telecom integration.

## Firebase configuration (required before any real backend feature works)

1. Create a Firebase project, register Android application **`com.aaspass.app`**. Put its own `google-services.json` at `app/google-services.json`. This file is intentionally absent and must never be committed.
2. Enable **Email/Password** in Authentication, **Cloud Firestore**, **Cloud Storage**, and **Cloud Messaging**. Use correct Firebase billing/permissions for Cloud Functions v2, scheduled functions, and Storage. Pick appropriate regional project settings; the functions are deployed to **`asia-south1`**.
3. Install Node.js 22 and Firebase CLI, then run at the project root:

   ```bash
   npm --prefix firebase/functions install
   firebase login
   firebase use --add
   firebase deploy --only firestore:rules,firestore:indexes,storage,functions
   ```

4. **Calls need a real TURN server**, securely operated (e.g. coturn with `use-auth-secret`, TLS and abuse controls). Point `TURN_HOST` at a hostname you actually control; configure `TURN_SHARED_SECRET` in Firebase Secret Manager via the Firebase CLI. It **must match** the shared secret configured on coturn. Example setup commands (replace host and secret yourself):

   ```bash
   firebase functions:secrets:set TURN_SHARED_SECRET
   # Set the Cloud Functions parameter TURN_HOST when asked during deploy.
   firebase deploy --only functions
   ```

   The backend supplies only time-limited credentials after authenticating active call participants. The code uses `turn:HOST:3478?transport=udp` and `turns:HOST:5349?transport=tcp`; these ports/protocols require corresponding working TURN listeners. Do not hardcode TURN secrets in the app or GitHub. Firebase calls will fail with an explicit setup error without this server. Test calls on Wi-Fi and cellular networks.

5. Storage Rules use **cross-service Firestore reads** to authorize chat/group media. Ensure Firebase project permissions and required service-agent authorization for Storage-to-Firestore rules checks are configured. Verify access with Storage Emulator before live uploads. File permissions intentionally deny updates, deletion and listing to Android clients; unused uploads require scheduled backend cleanup in production.
6. Open project root in Android Studio. Required: JDK 17, Android SDK 36, Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.20. The archive does **not** contain downloaded dependencies or an executable Gradle wrapper JAR; install Gradle 8.13 to build, or generate Gradle wrapper with `gradle wrapper --gradle-version 8.13`. Then run:

   ```bash
   gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
   ```

7. Sign into **two devices** using different test accounts; recipient turns ON `Settings → Allow username discovery` (default OFF), sends/accepts requests, confirms messaging/media, group invites, calls, logout and deletion. Reset Firebase test data after testing.

## Testing and security

- `npm --prefix firebase/functions run lint` validates backend JS syntax.
- `npm --prefix firebase/functions test` runs 21 pure backend utility tests, no Firebase credentials needed.
- `python qa/verify_project.py` validates XML/JSON, essential files, icons, and secret exclusion.
- `app/src/test/.../NotificationDestinationTest.kt` validates safe notification navigation in Gradle CI. Pure navigation smoke tests can be compiled with `kotlinc` using `qa/check_navigation.kt`.
- `qa/BUILD_REPORT.md` records tests run in this environment, separately from real Android builds.
- `.github/workflows/android.yml` runs checks and **attempts Android assemble + unit tests on GitHub Actions** once pushed. The workflow is not proof of a successful build until its run passes.
- `qa/BUILD_REPORT.md` documents exactly which checks ran here and which did not.
- `qa/RELEASE_CHECKLIST.md` lists the remaining release blockers and negative security test cases.

**Privacy:** Account and username discovery are private unless explicitly opted in. Raw contacts, GPS, and student/school information are not collected to identify users. Group membership requires explicit invitation acceptance. Block and report tools are basic—not a substitute for live human moderation. Database admins can access content; never advertise E2EE without a reviewed E2EE protocol. Review child safety, privacy notice, retention and deletion compliance before public deployment.

## v0.6 reliability hardening

- Latest Firestore window wins over paginated older snapshots, avoiding stale edited/deleted message text.
- Reply previews use a keyed index instead of repeated scans through all loaded messages.
- Missing or inaccessible groups now exit loading state and show an unavailable message.
- Group FCM delivery uses bounded concurrency (maximum 8 recipients in flight) instead of sequential sends; no guaranteed delivery-time claim.
- One duplicate Android FCM configuration property was removed from invite notifications.
- Pure Kotlin timeline tests and backend bounded-delivery tests added.

## Architecture

- `app/src/main/java/com/aaspass/app/` — Kotlin/Compose UI, repositories, WebRTC call session, FCM listener.
- `firebase/functions/` — authenticated groups, media publishing, calls, account deletion, notifications, expiry scheduler.
- `firebase/firestore.rules` — member-only reads/writes and role-based calling signaling.
- `firebase/storage.rules` — authorized per-conversation/group media paths, 20 MiB cap, MIME allowlist.
- `firebase/firestore.indexes.json` — call history, expiry and erasure collection-group indexes.
- `qa/` — automated static verifier and manual-release gates.

**Development principle:** An unconfigured feature should show a real error instead of reporting fictional success. This alpha remains incomplete until real Android and Firebase/WebRTC tests pass.

## Publishing status (18 November 2026)

This update was **not** pushed to GitHub or the Play Store. The source-only publication helper is `scripts/publish_source_github.sh --private` (or `--public` only after reviewing the repository); it requires authenticated GitHub CLI and an active network connection. The connected GitHub account has no dedicated AasPass repository, and the available GitHub integration cannot create a new repository. No Play Console credentials, Firebase production project, signed AAB, TURN server, or two-device acceptance test have been supplied. A source ZIP is provided; review `PUBLISH_HINDI.md` for a truthful release workflow. Do not market this project as tested or as E2EE.
