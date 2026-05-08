---
name: commit-local
description: Create local commits for this repository by strictly following the .gitmessage template and verifying Chinese text renders correctly after commit. Use when the user asks to write a compliant commit message and commit locally without pushing or release tagging.
---

# Commit Local

## Overview

Use this skill for local-only commit workflows. It enforces the repository commit template and validates that Chinese commit text is not garbled after commit.

## Workflow

1. Read `references/commit-rules.md`.
2. Check pending changes:
   - `git status --short --branch`
3. Write commit message in UTF-8 to a local message file (for example `.git/COMMIT_MSG.txt`).
4. Run:
   - `powershell -ExecutionPolicy Bypass -File .codex/skills/commit-local/scripts/commit-local.ps1 -MessageFile .git/COMMIT_MSG.txt`
5. Confirm the latest commit message text:
   - `git log -1 --pretty=%B`

## Scope Rules

- Stop after local commit and encoding validation.
- Do not push, tag, or publish releases in this skill.
- Keep message format strictly aligned with `.gitmessage`.
