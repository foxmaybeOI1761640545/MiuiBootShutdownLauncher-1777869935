---
name: version-release
description: Publish Git releases for this repository with a repeatable workflow for writing template-compliant commit messages, validating Chinese commit text rendering, pushing with token-based authentication, and creating the next version tag in the format vx.0.y. Use when the user asks to commit pending changes, push to GitHub, or publish a new release tag.
---

# Version Release

## Overview

Use this skill to standardize commit, push, and tagging operations for this project without leaking credentials.

## Workflow

1. Read `references/release-rules.md`.
2. Check repository state with `git status --short --branch`.
3. Build a commit message that strictly follows `.gitmessage`.
4. Stage and commit intended files.
5. Verify Chinese commit text by reading `git log -1 --pretty=%B` in UTF-8 output.
6. Compute the next tag with `scripts/next-release-tag.ps1`.
7. Push branch commits first, then push the annotated tag.

## Commit Message Rules

- Keep the subject format: `<type>(<scope>): <中文摘要> | <English summary>`.
- Fill all structured fields from `.gitmessage`.
- Write unavailable sections as `N/A`.
- Avoid placeholder text or missing sections.

## Version Tag Rules

- Keep tag format strictly `vx.0.y`.
- Keep `x` equal to the current major from the highest existing `v*.0.*` tag.
- Set `y` to the previous maximum plus one.
- Create an annotated tag with a short bilingual message.

## Security Rules

- Pass PAT through temporary environment variables or ephemeral git config.
- Never commit PAT, usernames, or email credentials into tracked files.
- Avoid printing full credential-bearing URLs in terminal output.
