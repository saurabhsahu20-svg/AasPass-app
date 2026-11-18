# AasPass v0.4 Developer Alpha — Hindi Setup Guide

**Yeh source project hai, tested APK nahi.** Bina Firebase configuration ke login, messages, upload, groups aur calls nahi chalenge.

1. ZIP extract karke Android Studio mein `AasPass/` root open karein. Package ID: `com.aaspass.app`.
2. Firebase Console mein isi package name se Android app register karein. `google-services.json` file `app/` folder mein rakhein. Is private config ko public GitHub par commit na karein.
3. Firebase Authentication ka **Email/Password** option enable karein. Cloud Firestore, Cloud Storage aur Cloud Messaging activate karein. Functions v2 ke liye billing/permissions check karein.
4. Machine par Node.js 22 aur Firebase CLI install karein. `npm --prefix firebase/functions install`, `firebase login`, `firebase use --add`, phir `firebase deploy --only firestore:rules,firestore:indexes,storage,functions` chalayen.
5. File uploads ke Storage rules Firestore group/chat membership check karte hain; cross-product service-agent permissions aur rules emulator mein read/write tests verify karein.
6. **HD voice/video calls** ke liye apna TLS-secured TURN/coturn server chalana zaroori hai. Host ko Firebase deploy ke dauran `TURN_HOST` parameter mein set karein aur usi server ki secret key Firebase Secret Manager mein `firebase functions:secrets:set TURN_SHARED_SECRET` se store karein. TURN ke bina connection establish hone ka claim na karein.
7. Android Studio mein JDK 17, SDK 36 aur Gradle 8.13 configure karein. ZIP mein Gradle wrapper JAR/scripts nahi hain; installed Gradle se `gradle wrapper --gradle-version 8.13` generate kar sakte hain.
8. `gradle :app:assembleDebug :app:testDebugUnitTest` chalakar build errors check karein. Optional: GitHub Actions par Android CI run verify karein.
9. Do test devices par do separate accounts banayein. Username discovery default OFF hai: recipient ko Settings se ON karna hoga. Chat invite accept/decline, send/edit/delete, offline queue, privacy aur block test karein.
10. Photo, video, PDF aur audio upload/download test karein. 20 MiB se bade files aur unauthorized access deny hone chahiye. Group invitation/member removal ke baad purane access ko test karein.
11. Voice/video calls ko Wi-Fi aur mobile data par, accept/reject/mute/switch camera/end ke saath test karein. FCM notification foreground, background, terminated aur account-switch states mein verify karein.
12. Firebase Emulator Rules tests, account deletion, device stress tests, accessibility, abuse moderation aur legal/privacy review pass hone ke baad hi release consider karein.

**Supported code:** Messaging, media sharing, private groups, Study group messages, 1:1 WebRTC call signaling + UI, data-only push and server-side account deletion.

**Abhi baaki:** E2EE, verified delivery marks, Google Sign-In, full-screen Telecom background calling, homework reminder scheduler, extensive abuse moderation, production account-erasure workflows, real Android/device certification. Har Android/Firebase/TURN feature ko real environment mein test karna zaroori hai. Zero-error guarantee nahi di ja sakti.
