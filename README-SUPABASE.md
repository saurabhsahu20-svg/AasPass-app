# MsgU — Firebase-free Internet Chat (Supabase)

This branch contains a **separate Android application**, not an update of the old Firebase build. The original main branch remains intact.

## Actual functionality in the source
- Email/password authentication through Supabase Auth; signup may require email confirmation.
- Users choose a unique lowercase username; only discoverable profiles can receive new chat requests.
- Private 1:1 requests, recipient acceptance/decline, secure row-level security policies.
- Real text messages to the Supabase database with client nonce de-duplication, visible error handling and background-safe network work.
- Foreground refresh plus Supabase Realtime on open chats, while visible.
- Profile screen, sign-out, offline Jelly Studio with 20 decorative Unicode reactions.

## Setup steps (necessary for messages)
1. Create a Supabase project under your own account at https://supabase.com/dashboard.
2. Open **SQL Editor** and run the exact `supabase/schema.sql` **once on a NEW project**. Review RLS policies. Never run unfamiliar SQL on existing production data.
3. Enable Email provider under Authentication, and set email verification settings appropriate to your testing.
4. Install the APK. On the Setup screen, enter **Project URL** like `https://YOUR_PROJECT.supabase.co` and **publishable/anon key**. Use only publishable/anon keys; NEVER use a service_role or secret key on an Android phone or in GitHub. The app stores the public URL/key locally.
5. Create two different accounts on two devices, verify emails if required, create username profiles, opt into discovery, send request from device A, accept from B, and test messages in both directions.

## Limitations
- This initial preview is not full WhatsApp. **Text-only private chat**: no phone-number account flow, media sending, calling, groups, push notifications, E2EE, delivery-to-device receipts, or production load testing.
- Supabase hosts the data and users; this is internet chat, not server-free messaging.
- Data is protected by Supabase RLS and TLS but **not end-to-end encrypted**. Project admins may access message content.
- App is a separate applicationId `com.msgu.internet`; it does not migrate old AasPass message history.
- No physical-device interoperability verification has been run unless separately documented.
- Debug APK is signed with GitHub runner's ephemeral debug key; not Play Store eligible or guaranteed upgrade-compatible.
