# AasPass v0.8.0-alpha — source QA and GitHub preparation

Date: 18 November 2026.

## Verified locally

- `npm --prefix firebase/functions run lint`: PASS. Node.js syntax checks on Cloud Functions source.
- `npm --prefix firebase/functions test`: **25/25 PASS**. Includes four new group notification delivery tests.
- `python3 qa/verify_project.py`: local structural, manifest, required asset, XML/JSON and basic secret checks. Recorded in the final validation run.
- `git init` and local initial commit: generated to make publishing reproducible. **Local Git is not a GitHub repository.**

## v0.8 changes

- Group notification sending is now best effort across recipients: a single Firebase delivery exception no longer prevents attempts for other recipients.
- Permanently invalid FCM tokens are deleted opportunistically after group multicast. Transient errors are not removed.
- Publishing script can initialize source Git history, or safely fast-forward an existing owned `AasPass-Android` remote. It refuses unknown remotes, dirty history and sensitive credentials.
- Extra Git ignore entries exclude build artifacts, Node modules, debugging files and common private key formats.

## Unverified — do not publish production APK yet

- Android Gradle compilation, release APK/AAB and full integration tests are **NOT run** here (no Gradle distribution, Android SDK or outbound Maven network).
- Firebase/Auth/Firestore/Storage security rules **NOT validated in a Firebase emulator** here; actual message latency and delivery not measured.
- TURN/WebRTC calls, background ringing and video calling not verified on actual devices.
- No production signing key, Privacy Policy or Google Play Console submission.
- **GitHub remote repository NOT created**: the connected GitHub action set cannot create repositories, and this runtime has no GitHub CLI credentials or network. The project is prepared for an authenticated `gh repo create` push using the included script.

A passing unit/static check is not evidence that the entire Android app is bug-free or release-ready.
