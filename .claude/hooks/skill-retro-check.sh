#!/usr/bin/env bash
# Stop hook: when test/framework code changed but no skill file did, block
# the stop ONCE per distinct diff and ask Claude to run the self-analysis
# skill (and update site-map with anything verified live). Never loops:
# skips when stop_hook_active is set, and remembers which diff it already
# nudged about.
set -euo pipefail

input=$(cat)
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

code_changes=$(git status --porcelain -- src build.gradle 'src/test/resources/*.xml' | sed 's/^...//' | sort -u)
[ -z "$code_changes" ] && exit 0

skill_changes=$(git status --porcelain -- .claude/skills)
[ -n "$skill_changes" ] && exit 0

state=$( { git diff -- src build.gradle; git ls-files --others --exclude-standard -- src; } | shasum | cut -c1-16)
marker="$(git rev-parse --git-dir)/claude-skill-retro-$state"
[ -e "$marker" ] && exit 0
touch "$marker"

files=$(printf '%s' "$code_changes" | head -10 | tr '\n' ' ')
jq -n --arg files "$files" '{
  decision: "block",
  reason: ("Code changed (" + $files + ") but no skill under .claude/skills was updated. Before finishing: run the self-analysis skill — did any skill instruction turn out wrong, missing or unnecessary during this task? If you walked the app in a browser or learned a navigation/locator fact, add it to the site-map skill (dated, ✅/⚠). If nothing is worth recording, say so in one line and stop — do not invent edits.")
}'
