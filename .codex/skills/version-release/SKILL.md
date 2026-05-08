---
name: version-release
description: Publish Git releases for this repository with a repeatable workflow for writing template-compliant commit messages, validating Chinese commit text rendering, using local-only PAT credentials for remote operations, and creating the next version tag in the format vx.0.y. Use when the user asks to commit pending changes, push to GitHub, or publish a new release tag.
---

# Version Release

## Overview

Use this skill to standardize commit, push, and tagging operations for this project without leaking credentials to tracked files.

## One-Time Local Auth Setup

1. Create local-only auth file at `.git/version-release-auth.env`.
2. Use the template in `references/local-auth.example.env`.
3. Run `scripts/setup-local-auth.ps1` once per repository clone.

## Workflow

1. Read `references/release-rules.md`.
2. Check repository state with `git status --short --branch`.
3. Build a commit message that strictly follows `.gitmessage`.
4. Save commit message into a UTF-8 message file.
5. Run `scripts/commit-local-with-check.ps1 -MessageFile <path>` to stage, commit, and verify message encoding.
6. Compute the next tag with `scripts/next-release-tag.ps1`.
7. Execute remote operations through `scripts/git-auth.ps1`:
   - Branch push: `scripts/git-auth.ps1 push origin main`
   - Tag push: `scripts/git-auth.ps1 push origin <tag>`

## Commit Message Rules

- Keep subject format: `<type>(<scope>): <Chinese summary> | <English summary>`.
- Fill all structured fields from `.gitmessage`.
- Write unavailable sections as `N/A`.
- Avoid placeholders or missing sections.

## Version Tag Rules

- Keep tag format strictly `vx.0.y`.
- Keep `x` equal to the current major from the highest existing `v*.0.*` tag.
- Set `y` to the previous maximum plus one.
- Create an annotated tag with a short bilingual message.

## Security Rules

- Keep PAT in `.git/version-release-auth.env` only.
- Never store PAT in tracked files, commit messages, tag messages, or script defaults.
- Use `scripts/git-auth.ps1` for remote operations to inject PAT only at runtime.
- Avoid printing full credential-bearing URLs in terminal output.
