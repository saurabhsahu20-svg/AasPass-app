# MsgU 0.9.1 — Connect Internet Chat (Email or Phone)

This beta uses **your own Supabase backend**. There is no built-in shared server; without project setup, messages cannot reach other phones.

1. Create a project in https://supabase.com/dashboard and wait for the database to become active.
2. Open **SQL Editor → New query**, copy the entire file `supabase/01_msgu_chat.sql`, run it, and review success. This configures private profiles, messaging and Row Level Security (RLS). Do not enable unrestricted anon table access.
3. Open **Authentication → Providers** and enable **Email**. For testing, be aware Supabase's default SMTP is restrictive; email confirmation generally requires a configured custom SMTP service for users outside your project team. You can use test accounts under suitable controlled test settings.
4. Optional **Phone login**: **Authentication → Providers → Phone → Enable** and configure an SMS provider (such as Twilio, MessageBird or Vonage). SMS deliveries may cost money. In India, check telecom DLT requirements and provider support. You can use Supabase test phone numbers and static OTPs for isolated testing. Follow https://supabase.com/docs/guides/auth/phone-login. Set rate limits and CAPTCHA appropriately before wider distribution.
5. Open project **Connect / Project URL** and **Settings → API Keys**; copy only the **Project URL** (`https://PROJECT.supabase.co`) and **publishable API key** (`sb_publishable_...`). **Never put a service_role/secret key or your account password in the APK or GitHub.**
6. Install the beta APK on two devices. Enter the **same Project URL and publishable key** on both (they share a backend). Each user logs in with a different Email/Password, or (if configured) Mobile OTP.
7. In **Profile**, set a unique username and enable **Allow username discovery** for the intended recipient. The sender enters that exact username under **New chat**. Recipient opens MsgU and **Accepts** the chat request. Messages can now be exchanged.
8. The chat view polls about every 2.5 seconds. This is not push notification or WhatsApp-style E2EE. It does not have background notifications, group calling or attachments on Supabase. Do not use the beta for sensitive conversations.

**Testing requirement:** GitHub Actions compiling the APK is not proof that SMS/email delivery or two-device messages work. Test both with a configured project. Phone numbers and OTPs must never be collected in app logs or shared with other people.

**APK installation note:** GitHub Actions debug builds use temporary signing certificates; Android may refuse to install an update over a differently signed APK. Back up important local data before any uninstall.
