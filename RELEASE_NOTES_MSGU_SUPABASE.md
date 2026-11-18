# MsgU v0.9.0 • Firebase-free Internet Chat Preview

This is an early Android testing application that connects to **your own Supabase project**. It does **not** use Firebase. It is a separate app from the original AasPass build.

## Before sending messages
1. Create a Supabase project under your account.
2. Read and execute `supabase/schema.sql` from the **msgu-supabase-internet-chat** branch using your project's SQL Editor, on a NEW database.
3. Enable Email authentication (email verification may be required).
4. On the phone, open MsgU and enter your own Supabase **Project URL** and **public/publishable** key. Do not enter a service-role or secret key.
5. Create two test users, select two usernames, send and accept a private chat request, then test the two-way messaging.

No user credentials, service keys or personal backend project configuration are bundled with this APK.

## Implemented in source
- Login/signup through Supabase Auth and encrypted local session tokens.
- Search by exact username; explicit chat request acceptance.
- Private text messaging; backend-enforced row-level-security; stable send nonce to reduce duplicates after retry.
- Realtime WebSocket updates for active chats, with a foreground network-refresh fallback.
- 20 Unicode jelly-style emoji previews and emoji composer, profile/discovery setting, block user.
- Android native Jetpack Compose UI, light and dark themes.

## Not included
- Not the full WhatsApp feature set.
- No voice/video call, group chat, photo attachments, phone-number login, push notifications, custom sticker image sending, E2EE or server-proven device delivery receipts.
- The Android build and unit tests are verified in GitHub Actions, **not** the end-to-end behavior with a live Supabase project or physical devices.
- Do not use this preview for confidential communications. Supabase project operators can access plaintext stored messages.
- Debug APK signed using an ephemeral GitHub Actions debug key, which may prevent upgrading in place.
- This application has package ID `com.msgu.internet`; it installs separately from the old Firebase-based `com.aaspass.app`.

Source branch: https://github.com/saurabhsahu20-svg/AasPass-app/tree/msgu-supabase-internet-chat
