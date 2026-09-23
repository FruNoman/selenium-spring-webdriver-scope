---
name: self-analysis
description: Close the loop on a task that followed a skill — compare what actually happened with what the skill said, and edit the skill itself when reality diverged (a missing step, a wrong assumption, an instruction that proved unnecessary or harmful, a decision the user corrected). Use at the end of multi-step work in this repo, whenever the Stop hook asks for it, or when asked to self-analyze / retro.
---

# Closing the loop on a skill

A skill is a hypothesis about how a task should go. Every real run confirms it
or exposes a gap — and a gap not written back costs the same detour next time.
This repo's Stop hook (`.claude/hooks/skill-retro-check.sh`) asks for this pass
once whenever code under `src/` changed but no skill file did.

## Questions to answer honestly

1. Which skill(s) did this task follow? Where did reality differ from them?
2. **Which of my decisions turned out wrong** — a locator I trusted, a cause I
   assumed, a step I skipped, a run I didn't need? What would have prevented it?
3. Did the user correct me ("no, do X instead")? That's a rule to record.
4. Did something work that no skill mentions and will plausibly recur?

## Worth recording vs. not

| Record it | Don't |
|---|---|
| A tool, field, selector or command the skill assumed, which turned out wrong (verified by a real check) | A one-off slip where the skill was actually right |
| A step that, followed literally, produced wasted work | Friction outside anyone's control (a genuinely flaky external site) |
| A new situation likely to recur, not covered yet | A hypothetical you haven't hit |
| An explicit user correction | A one-time preference for this task only |

Don't overfit a process to one ambiguous incident — if unsure whether it's a
real gap, write it with ⚠ and say it's from a single occurrence.

## How to edit

- **Replace, don't append** — rewrite the wrong instruction in place; no
  contradictory pairs.
- **Say why** in a clause — rules without reasons get deleted by the next edit.
- **Mark confidence** — "verified <date>" vs "⚠ single occurrence".
- **Don't bloat** — one sentence in an existing section beats a new heading.
- Facts about the *app under test* (URLs, locators, UI quirks) go to
  `site-map`; facts about *how to work* go to the skill that governs that work.

## Reporting

Mention small corrections in one line of the final report. A substantive
change — dropping a rule, changing a threshold, reversing a recommendation —
gets an explicit callout, since it changes how the next task runs. If nothing
was worth recording, say so in one line; don't invent edits to satisfy the hook.
