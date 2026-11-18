# MsgU 0.9.3 — User setup and reliability

**Source project:** MsgU uses a personal Supabase backend for authentication and chat, with no Firebase needed for the Supabase screen. It is a **beta APK**, not verified WhatsApp-equivalent or E2EE.

## One-time connection
1. Create an account and project in https://supabase.com/dashboard .
2. Open your Supabase project's SQL Editor, run the complete `supabase/01_msgu_chat.sql` file. The SQL creates the `msgu_profiles`, `msgu_chats`, and `msgu_messages` tables, Row-Level Security policies, and `msgu_start_chat` function. If you previously ran the older file, run the updated file again so idempotent message IDs are allowed.
3. In Supabase Auth Providers, enable **Email**. For standard confirmation emails, configure an approved SMTP provider. If this is a student project, have a parent/guardian help with any account or payment setup.
4. For optional **Phone OTP**, enable Phone auth and set up a supported SMS provider. SMS may incur costs. You can test using Supabase's test phone numbers and OTPs instead of purchasing SMS during development. Do not collect or share personal OTPs.
5. Copy the **Project URL** and **Publishable Key** only (NOT secret/service_role key). Enter both on the first MsgU setup screen; both devices must use the **same** project. Never share passwords, OTPs or project admin secrets.
6. Create two accounts and confirm their emails if required, or verify a phone number with SMS. Set a username and enable 'Allow username discovery' from the recipient's profile.
7. Sender uses **+ New chat → exact username**. Recipient opens MsgU and **accepts**. Both can exchange messages. New chats refresh about every 5 seconds, open messages about every 2.5 seconds.

## Expected behavior and limitations
- Chat list now shows contact names from mutually accessible profiles rather than raw UUIDs.
- Each outgoing message has an idempotency UUID, so **retry after an uncertain timeout uses the same ID** and should not create a duplicate. This requires the updated SQL grant.
- The composer preserves text after failed sends and users can manually retry. A successful send clears text before attempting a best-effort refresh.
- While reading old messages, a new incoming message should not forcibly jump the scroll to the bottom.
- Email and SMS work only when configured with real Supabase providers; we cannot claim actual OTP delivery without testing.
- Not implemented on the Supabase path: end-to-end encryption, background notifications, group chat, media transfer, voice/video calls, reliable delivery receipts, WhatsApp-grade performance.
- Chat data is on your Supabase project, protected with RLS; project administrators may access unencrypted chat text.

## Android signing
GitHub Actions builds a debug APK with a runner-specific signing certificate. APK updates from different runs may require uninstalling the old debug app, so preserve important data before any reinstall. A stable production signing key must be configured privately for public releases.

The GitHub Actions build verifies Kotlin/Android compilation and unit/backend tests, but does **not** validate real two-device chat, production reliability or Supabase project setup.
