# MsgU v0.9.5 — Email Verification Fix (Debug Beta)

This build improves email sign-up and confirmation handling. Signup without a verified session is shown as **waiting for verification**, and the app includes a **resend confirmation** action with a cooldown. Auth errors include actionable messages for email-not-confirmed, unauthorized recipient, and server email rate limits. It also adds unit tests for Auth error handling.

**Email delivery requires a custom SMTP service** in the user's Supabase project. The Supabase default mailer sends only to project team members and has strict rate limits; project logs showed `over_email_send_rate_limit`. Nothing in this APK bypasses verification or provider restrictions. Confirm Email is deliberately left enabled.

See `SUPABASE_SETUP_MSGU.md` for SMTP configuration steps. No server SMTP credentials have been installed. This is an Android Debug Beta, not a production release, with no E2EE, live two-device verification or production security certification.