# MsgU Supabase Beta 0.9.0

This is an **internet-chat beta**, not WhatsApp feature parity. It uses your own Supabase project instead of Firebase for the new flow when no Firebase config is installed.

## Required one-time backend setup
1. Create a Supabase project: https://supabase.com/dashboard
2. Open **SQL Editor**, paste and run `supabase/01_msgu_chat.sql`. This creates profiles, private chats, messages and Row Level Security.
3. Go to **Project Settings → API Keys** and copy the **publishable key** (`sb_publishable_...`) and **Project URL** (https://...supabase.co).
4. Install the debug APK, enter those two public connection values on its setup screen.
5. Sign up with email/password on two devices, **verify the confirmation emails** if confirmation is enabled.
6. In Profile set a unique username, and choose **Allow username discovery** to receive new requests. Search the exact username from the other account, accept the request, then send messages.

### Privacy and reliability
Messages are accessible only to accepted participants by RLS. SQL does not allow anonymous message reads. Email is not listed in profiles. Messaging uses HTTPS and is **not end-to-end encrypted**. App refreshes the open conversation every ~2.5 seconds (polling, not instant WebSocket push) and doesn't provide background notifications, typing indicators, media messages or voice/video calling on this Supabase path. Login sessions are stored in private Android preferences; this is a testing implementation and has not undergone independent security review. Failed sends keep their drafts.

**Never paste a service_role key or secret key into the app.** Never commit your actual keys, passwords or private records to this public repository.

The existing Firebase implementation remains in source for backward compatibility, but a new APK built without `google-services.json` shows the Supabase experience.

Run CI: `gradle :app:assembleDebug :app:testDebugUnitTest`. Device-to-device messaging requires a configured Supabase project and two physical-device tests. Signed production releases require a stable signing key and further testing.
