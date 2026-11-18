# AasPass 💚

**Connect • Chat • Call • Learn**

AasPass is a Kotlin/Jetpack Compose Android messaging application with Firebase backend and in-progress WebRTC calling, groups and Study Hub.

**Release status:** Developer Alpha — not yet production-tested, signed or published to Google Play.

## Development / release requirements

1. Verify the full Android build and Android unit tests on CI.
2. Configure your own Firebase project, Cloud Functions, security rules and Storage; never commit credentials.
3. Configure a TURN service and perform real voice/video-call testing on two Android devices.
4. Test notifications, messaging, offline/reconnect, group invitations, account deletion, media uploads, and accessibility.
5. Publish a signed Android App Bundle only after all checks pass.

Project source import and GitHub Actions build workflow are being set up.
