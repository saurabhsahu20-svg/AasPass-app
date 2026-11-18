# MsgU 0.9.3 — Internet Chat Beta (Debug)

This is an early testing build: **a working Android build, not a fully configured or verified messaging service**. Install the APK, then connect a personal Supabase project before trying login or chat.

## Features included
- Email/password registration and sign-in through Supabase Auth.
- Optional phone-number login using SMS verification, when Phone Auth and an SMS provider are enabled in Supabase.
- Private two-person chat requests, accepting or declining invitations, username discovery controlled by the recipient, and text message history.
- Contact names in chat list when visible to participants.
- Retry-safe outgoing text messages: each draft attempt uses one client UUID, so retrying a timeout does not create multiple rows with the same id.
- Manual retry after send failures; calmer chat auto-scrolling.
- Open chat refresh every ~2.5 seconds, chat list every ~5 seconds (polling; NOT instant push).

## Mandatory project setup
1. Create a Supabase project at https://supabase.com/dashboard .
2. Run **the latest** `supabase/01_msgu_chat.sql` in that project's SQL Editor (even if you used an older version previously).
3. In Supabase Authentication, enable Email. For confirmation emails to external users, set up custom SMTP. Optional phone OTP also needs an SMS provider and may incur fees.
4. Copy only your **Project URL** and **publishable API key**, then enter them in MsgU's first screen on both devices. **Do not enter any secret/service_role key or passwords in GitHub.**
5. Make two separate accounts, verify Email/OTP, set a username (recipient opts into discovery), exchange an approved chat request, and test sending messages on two devices.

## Critical limitations
- **No Supabase project is bundled**; this APK cannot send messages between devices until the project has been created and configured.
- Not end-to-end encrypted; Supabase project administrators can access message contents.
- No background push notifications, groups, media sharing, voice/video calls, typing or read receipts in this Supabase beta.
- Physical phone tests, production security review, and real email/SMS delivery tests are not yet complete.
- Built with GitHub Actions' ephemeral **debug signing key**, NOT a stable production release certificate. Do not uninstall an old build without saving important local data.

Source code and setup: `SUPABASE_SETUP_MSGU.md` at the repository root.