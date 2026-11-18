# MsgU v0.9.4 — Already connected to the user's Supabase project

The new MsgU APK embeds only the **public project URL and publishable key** of the Supabase project created on November 18, 2026. Users no longer need to manually enter those details on a clean install. No service_role/secret key is embedded.

The backend has been created and the SQL schema in `supabase/01_msgu_chat.sql` applied, with three private RLS tables, profile auto-creation and a restricted chat-request function. The app and database are not end-to-end encrypted.

## Start chatting
1. Install the connected APK. Use **Email Login → Create Account** or choose **Mobile OTP** only after phone auth and an SMS provider have been configured.
2. Email users must complete email confirmation when enabled. Supabase default email delivery is restricted; custom SMTP may be required before outside users can sign up. A project administrator must configure SMS and country rules for phone OTP; charges may apply.
3. Both people must register on this SAME Supabase project. In Profile, set a unique username. The recipient chooses **Allow username discovery**.
4. Sender taps **+ New chat**, enters the recipient's exact username and sends the request; recipient opens MsgU and selects **Accept chat request**.
5. Send messages. The beta polls open chats about every 2.5 seconds. After a timeout, manual retry reuses a client-generated UUID to avoid duplicate messages.

## Important limitations
- GitHub CI compilation and unit/backend checks are not real two-device tests. Physical-device login, email delivery, phone OTP, actual message latency, and robustness remain unverified.
- No WhatsApp-grade E2EE, push notifications, voice/video calls, or group chat on this Supabase path. The older Firebase-based feature code does not make those features work on this path.
- The debug APK is signed by a temporary GitHub runner key. A differently signed update may not install over it; **back up local app data first**, do not uninstall without considering data loss.
- Do not share passwords, phone verification codes or service_role/secret keys. Project URL and publishable key are public client identifiers but must be paired with working database RLS.

Supabase project: `iatntpqytgzlfilveykd` (India / Mumbai region `ap-south-1`). The account owner must manage SMTP, SMS providers, consent and production policies through the Supabase dashboard.
