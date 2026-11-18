# AasPass GitHub Setup (Hindi)

AasPass ka source local Git commit ke saath taiyar hai. ChatGPT ki linked GitHub integration existing repositories update kar sakti hai, lekin new repository create karne ki permission/action nahi deti. AasPass ko kisi unrelated repository mein upload nahi kiya gaya.

## Publish command (authenticated computer)

1. Git, GitHub CLI (`gh`), Python 3 aur Node.js install karein.
2. `gh auth login` chalayen aur apne account se authorize karein.
3. Source folder ke andar `bash scripts/publish_source_github.sh --private` chalayen.
4. Script mandatory static tests chalayegi, GitHub par `AasPass-Android` repository create karegi (agar abhi nahi bani hai), aur `main` branch push karegi.
5. GitHub Actions par Android build ka result check karein. Build fail hone par error fix karein.

Source ko public karne se pahle privacy/security review aur real-device tests zaroor karein. Public source publish karna app ko Play Store par release karna nahi hai. Signing keys, Firebase service accounts ya `google-services.json` kabhi public repository mein commit na karein.

Manual alternative: GitHub ke `+` menu se **New repository** select karke `AasPass-Android` naam se **empty** repository banayein; source folder mein `git remote add origin https://github.com/<your-login>/AasPass-Android.git` aur `git push -u origin main` chalayen. `--force` ka use na karein.
