# MsgU v0.8.2 Smooth — Android Debug Preview

**This is an early testing APK, not a production release.** Built from the public MsgU source project. Android automated compilation and unit/backend tests are run by the publishing workflow.

## What's included
- MsgU app name and green-themed messaging screens.
- Offline Jelly Studio demo containing 20 **Unicode reactions** (not the separate Crystal Jelly PNG asset pack), Hindi/English keyword search, favourites, recent reactions and text sharing.
- Message thread improvements: smoother initial navigation, avoid discarding existing conversation UI on certain errors, and restore failed outgoing text to the composer for manual retry.
- Bounded conversation and message listeners, pagination, and persistent Firestore offline cache code.

## Important setup and limitations
- **Real login, chat, and calling are NOT ready out of the box.** This public debug APK does not contain a private Firebase `google-services.json`; add your own Firebase project to the source and configure the backend to test real chatting.
- Sending a message is not guaranteed when offline; the app may show a queued state until the server accepts it. Failed sends can be restored manually for retry. No guaranteed delivery time.
- The app **does not have end-to-end encryption**. Do not use for sensitive conversations.
- Calling requires a working TURN server, Firebase Functions and device testing. Animated custom stickers and the 500+ asset library are not part of this build.
- APK is signed using the GitHub runner's **ephemeral Android debug signing key**. Future debug APKs may have a different signing certificate, preventing in-place updates. Back up important app data before reinstalling; do not uninstall an existing app without considering local data loss.
- Compatibility: Android 8.0+ (minSdk 26). The applicationId remains `com.aaspass.app` to preserve project compatibility.

Source: https://github.com/saurabhsahu20-svg/AasPass-app

**Testing status:** GitHub CI compilation and unit/backend checks only. Physical-device performance, real Firebase messaging and production security have not been independently verified.
