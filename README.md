# MsgU AI Studio v1.1 — Android APK CI source

This branch intentionally isolates the user's *newer* Google AI Studio MsgU Android project from the older AasPass-app main branch. The input ZIP is the source from the 19 November 2026 upload, with targeted stability fixes. It preserves applicationId `com.aistudio.msgu.kqxrmv` and uses the user's existing Firebase project identifiers.

The ZIP is optimized for APK compilation (all Kotlin code, Android resources and 20 optimized Crystal Jelly emoji assets). Non-runtime design sheets and third-party font binaries are excluded. Original full source was provided separately in the conversation.

## Release workflow
Push to this branch runs Android Gradle build and unit tests. If successful, the workflow uploads the debug APK to Actions Artifacts and publishes a GitHub prerelease tag `msgu-ai-studio-v1.1.0-beta`. If compilation or tests fail, no release is published.

## Cautions
This is a CI **debug-signed** test build, not a production-signed app, and it cannot update a differently signed installed APK. Google Login may reject the newly generated signing SHA unless that SHA is registered in Firebase Auth/Google OAuth. An APK build does not prove live two-device chat works. Firebase rules must be deployed and tested independently. The included `google-services.json` is restricted to MsgU Android client entries and contains public client configuration only; **no administrator keys, tokens or keystores** are stored in this branch.

The existing `main` branch is left unchanged.
