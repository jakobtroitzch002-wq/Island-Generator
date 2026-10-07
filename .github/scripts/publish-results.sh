#!/usr/bin/env bash
# Legt Build-/Testergebnisse in einem eigenen Branch ab, damit man sie ohne Download-Login ansehen kann.
set -u
BRANCH="$1"; shift
OUT="$(mktemp -d)"
for f in "$@"; do
  [ -e "$f" ] && cp -r "$f" "$OUT/" 2>/dev/null || true
done
echo "commit: ${GITHUB_SHA:-?}" > "$OUT/INFO.txt"
cd "$OUT"
git init -q
git checkout -q -b "$BRANCH"
git add -A
git -c user.name="github-actions" -c user.email="github-actions@users.noreply.github.com" commit -q -m "Ergebnisse fuer ${GITHUB_SHA:-?}"
git push -q -f "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$BRANCH:$BRANCH" || echo "Push der Ergebnisse fehlgeschlagen"
