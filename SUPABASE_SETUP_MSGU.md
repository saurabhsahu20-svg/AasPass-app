# MsgU v0.9.5 — Email confirmation troubleshooting

The app is already connected to Supabase project `iatntpqytgzlfilveykd`. **No custom project URL or API key needs to be entered on a clean install.**

### Confirm Email is intentionally ON
The Supabase default SMTP sender only delivers to project team addresses and has very strict rate limits. Project Auth logs recorded `over_email_send_rate_limit` (HTTP 429) for a signup/email action. This is a server-side delivery limitation: an app-only update cannot make emails appear.

**To enable confirmation emails to any real user:**
1. Choose a legitimate SMTP service with a verified sender/domain (e.g., Resend, Brevo, Postmark or another provider).
2. Go to your Supabase project dashboard → **Authentication → SMTP Settings**.
3. Enable **Custom SMTP**, then enter the provider's SMTP host, port, username, password, verified sender email, and sender name `MsgU`. Keep credentials **only in the Supabase dashboard**, never in the public Android app or GitHub.
4. Save, confirm your sender's SPF/DKIM/DMARC when instructed by the provider, then test with a new email address and inspect Auth logs. Observe SMTP service restrictions and costs.
5. Keep email confirmations enabled. Disabling the check would allow people to register with emails they do not own. Use CAPTCHA and appropriate rate limits before broader release.

### In the Android beta
- Tap **Email → New user? Sign up** and enter your email, password and display name.
- If Supabase returns an account without a session, MsgU now displays a confirmation-needed message instead of silently treating the account as signed in.
- Open confirmation link from your email's Inbox or Spam, then return to the app and **Log in with Email**. The link opens in your browser; you should sign in manually afterward.
- There is a **Resend confirmation email** button with a 60-second UI cooldown. Resend cannot override SMTP restrictions, sender verification, or the server's rate limits.
- The app identifies `email_address_not_authorized`, `email_not_confirmed`, and `over_email_send_rate_limit` errors and gives useful instructions.

**Mobile OTP** requires an enabled SMS provider and possible paid messaging setup. It is not a substitute for unconfigured SMTP unless the SMS provider has also been set up.

**App limitations:** Android Debug Beta; not end-to-end encrypted; no background notification or WhatsApp-like calls on Supabase path. APK compilation and unit tests do not prove actual email deliverability or two-device messaging works.
