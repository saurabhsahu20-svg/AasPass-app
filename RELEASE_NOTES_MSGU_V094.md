# MsgU v0.9.4 — Connected Chat Beta (Debug)

This new preview is connected on a clean install to the **MsgU Supabase project** deployed in India (Mumbai). It contains a public publishable API key, never a service-role key.

Included: Email/password signup and login, optional Phone OTP (requires an SMS provider), user profiles and discoverability, two-person chat requests and acceptance, private text messaging history, idempotent send retries, contact names, and improved scrolling. Server database includes three RLS-protected tables and an authenticated-only chat creation endpoint.

**Set up before use:** Enable/verify Email Auth and email delivery in Supabase. For phone SMS OTP configure a supported SMS provider and ensure compliance with SMS regulations. The Supabase project currently has no users; create separate accounts and test on two devices.

**Not yet delivered/verified:** End-to-end encryption, push notifications, WhatsApp-like calls, group chat, media transfer, real world email/SMS delivery and two-device message latency. This is a **debug beta**, not a signed Play Store release. GitHub Actions' ephemeral signing key can prevent updating a different debug APK in place.

Full instructions: `SUPABASE_SETUP_MSGU.md` in this repository.