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
3. Fetch remote tags: `scripts/git-auth.ps1 fetch origin --tags`.
4. Prepare next release version **before commit**:
   - Run `scripts/prepare-next-version.ps1`.
   - Optional pre-check: `scripts/prepare-next-version.ps1 -DryRun`.
   - This script computes the next tag, updates `package.json`, `package-lock.json`, and `android/app/build.gradle`, bumps `versionCode`, removes BOM risk, and checks for invalid characters.
5. Build a commit message that strictly follows `.gitmessage`.
6. Save commit message into a UTF-8 (no BOM) message file.
7. Run `scripts/commit-local-with-check.ps1 -MessageFile <path>` to stage, commit, and verify message encoding plus staged text-file sanity checks.
8. Execute remote operations through `scripts/git-auth.ps1`:
   - Branch push: `scripts/git-auth.ps1 push origin main`
   - Tag push: `scripts/git-auth.ps1 push origin <tag>`
9. Create an annotated tag message using ASCII-only text when possible to minimize encoding risks in release tooling.

## Commit Message Rules

- Keep subject format: `<type>(<scope>): <Chinese summary> | <English summary>`.
- Fill all structured fields from `.gitmessage`.
- Write unavailable sections as `N/A`.
- Avoid placeholders or missing sections.

## Version Tag Rules

- Keep tag format strictly `vx.0.y`.
- Keep `x` equal to the current major from the highest existing `v*.0.*` tag.
- Set `y` to the previous maximum plus one.
- Sync version files to `${x}.0.${y}` **before commit**.
- Create an annotated tag with a short bilingual message.

## Security Rules

- Keep PAT in `.git/version-release-auth.env` only.
- Never store PAT in tracked files, commit messages, tag messages, or script defaults.
- Use `scripts/git-auth.ps1` for remote operations to inject PAT only at runtime.
- Avoid printing full credential-bearing URLs in terminal output.
- Avoid introducing invalid text bytes (UTF-8 BOM, U+FFFD, control chars) in committed text files.
