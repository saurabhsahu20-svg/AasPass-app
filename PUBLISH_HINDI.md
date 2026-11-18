# AasPass — GitHub aur Play Store publication roadmap

**Vartamaan sthiti: v0.6.0-alpha SOURCE ONLY (not published).** Public production release ko Android build, Firebase + TURN configuration, security tests aur signed artifact ke bina approve na karein.

## 1. Dedicated GitHub repo

1. Apne connected GitHub account mein naya repository **AasPass-Android** banayein (aapki choice ke hisaab se private pehle rakhein; security review ke baad public karein).
2. Is ZIP ka **poora AasPass-Android project root** extract karein (sirf source files, ZIP file khud nahi).
3. Affected machines mein `git init -b main`, `git add .`, `git commit -m "AasPass v0.6 developer alpha"`, `git remote add origin <YOUR_REPO_URL>` aur `git push -u origin main` chala sakte hain.
4. `.github/workflows/android.yml` ka **android-build** job green hone tak release tag na lagayein. CI debug APK sirf internal testing ke liye hai, Play Store artifact nahi.
5. Kisi bhi repo mein `google-services.json`, `.env` secret, TURN shared secret ya signing key commit **na** karein.

## 2. Firebase aur calling configuration

1. Android package `com.aaspass.app` ko apne Firebase project mein add karein. `app/google-services.json` sirf local/secure CI mein add karein.
2. Email/Password Authentication, Firestore, Storage, FCM aur Cloud Functions ko configure karein.
3. `firebase deploy --only firestore:rules,firestore:indexes,storage,functions` se pehle emulator security tests chalayein aur access rules manually audit karein.
4. Calling ke liye production coturn + working TURN credentials + network tests zaroori hain. `TURN_SHARED_SECRET` ko server-side Secret Manager mein rakhein.
5. Firebase App Check aur abuse-rate protection ko carefully implement/test kiye bina broad public signup na dein.

## 3. Device testing aur performance

- Kam-se-kam do devices aur Wi-Fi/mobile data par login, 1-to-1 text, image/PDF uploads, message acknowledgements aur invite flows test karein.
- Call accept/reject/reconnect, background notification, screen locked state, permission denial, Bluetooth, camera off, app kill/restart ke cases test karein.
- Android Studio profiler/Macrobenchmark aur Firebase Crashlytics se jank, startup, FCM latency, memory, crashes, ANRs aur energy use measure karein. **Kisi ek device ka subjective 'smooth' test release quality prove nahi karta.**
- Privacy disclosure, abuse reporting, blocking, deletion, school/student safety review, accessibility aur account data handling finalize karein.

## 4. Signed testing release aur Google Play

1. Secure keystore create karein (GitHub mein kabhi commit nahi), release signing sirf secure CI/local signing environment mein configure karein.
2. Successful CI + Firebase staging tests ke baad `:app:bundleRelease` se signed `.aab` build karein.
3. Play Console app entry, official logo, screenshots, privacy policy URL, age/content declarations, data-safety answers aur permissions disclosures complete karein.
4. Internal testing → closed testing → staged rollout mein go/no-go gates pass karein. Google Play ke current requirements console mein check karein.
5. Rollout monitor karein aur rollback plan ready rakhein. Real production launch tab tak mat karein jab tak `qa/RELEASE_CHECKLIST.md` ke blockers close na hon.

**Current blockers:** Dedicated AasPass GitHub repo nahi mila; repo-create operation connected tool mein available nahi; local Android SDK/Gradle dependency environment unavailable; Firebase creds/production deployment aur TURN infra absent; physical device testing aur signing keys absent. Isliye source archive hai, public release/Play publication nahi.


## Optional source-only publication script

Authenticated GitHub CLI (`gh auth login`) aur network access ke saath, **new**, clean extracted project root se:

```bash
bash scripts/publish_source_github.sh --private
# Ya reviewed source ko public karne ke liye:
bash scripts/publish_source_github.sh --public
```

Script existing GitHub repo ko overwrite nahi karega. Yeh sirf source ko push karta hai; APK, Play Store app, Firebase deployment, ya signed release publish nahi karta. Pehle GitHub Actions build result check karein.
