#!/usr/bin/env bash
# Publish only AasPass source, never an APK, Firebase secrets or signing keys.
# Requires: git, Node.js, Python 3, and a GitHub CLI authenticated as the repo owner.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
visibility="${1:---private}"
if [[ "$visibility" != "--private" && "$visibility" != "--public" ]]; then
  echo 'Usage: scripts/publish_source_github.sh [--private|--public]'
  exit 2
fi
for cmd in git gh python3 node; do
  command -v "$cmd" >/dev/null || { echo "Missing tool: $cmd"; exit 1; }
done
if [[ -e app/google-services.json ]] || find . -type f \( -name '*.jks' -o -name '*.keystore' -o -name '.env' -o -name 'service-account*.json' -o -name 'firebase-adminsdk*.json' \) -print -quit | grep -q .; then
  echo 'Refusing upload: local Firebase credentials, signing keys, or environment secrets detected.'
  exit 1
fi
python3 qa/verify_project.py
npm --prefix firebase/functions run lint
npm --prefix firebase/functions test
if [[ ! -d .git ]]; then
  git init -b main
  git add .
  git commit -m 'Initial AasPass Android source: developer alpha'
fi
if [[ "$(git branch --show-current)" != "main" ]]; then
  echo 'Refusing to publish from a branch other than main.'
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo 'Uncommitted changes found. Commit and review them before publishing.'
  exit 1
fi
gh auth status >/dev/null
login="$(gh api user --jq .login)"
repo="${login}/AasPass-Android"
expected="https://github.com/${repo}.git"
if git remote get-url origin >/dev/null 2>&1; then
  remote="$(git remote get-url origin)"
  if [[ "$remote" != "$expected" && "$remote" != "git@github.com:${repo}.git" ]]; then
    echo "Refusing to publish: origin points somewhere unexpected: $remote"
    exit 1
  fi
fi
if gh repo view "$repo" --json name >/dev/null 2>&1; then
  # Existing repository is allowed, but push must fast-forward. Never force push.
  if ! git remote get-url origin >/dev/null 2>&1; then
    git remote add origin "$expected"
  fi
  git push -u origin main
else
  # Creates a repository on the signed-in owner's account, then pushes the local history.
  gh repo create "$repo" "$visibility" --source=. --remote=origin --push
fi
echo "GitHub SOURCE repository: https://github.com/${repo}"
echo 'This is developer-alpha source only. CI, Firebase, security and real-device tests are required before public release.'
