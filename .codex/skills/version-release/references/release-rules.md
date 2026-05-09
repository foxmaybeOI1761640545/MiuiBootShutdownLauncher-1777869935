# Release Rules

## Local PAT Storage Rule

- Keep real credentials in `.git/version-release-auth.env` only.
- Use `local-auth.example.env` as a template reference.
- Do not commit any file containing real PAT.

Example local file content:

```text
GITHUB_USER=your-user
GITHUB_EMAIL=your-email@example.com
GITHUB_PAT=github_pat_xxxxxxxxxxxxxxxxxxxxxxxxx
GITHUB_REMOTE=origin
```

## Local Auth Initialization

Run once in a repository clone:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/setup-local-auth.ps1
```

This command updates local `.git/config` only and never writes PAT to tracked files.

## Version Preparation Rule (Before Commit)

Always determine and apply the next version **before** creating the release commit:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 fetch origin --tags
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/prepare-next-version.ps1
# Optional pre-check only:
# powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/prepare-next-version.ps1 -DryRun
```

Expected behavior:

- Compute next tag in strict format `vX.0.Y`.
- Set app version to `X.0.Y` in:
  - `package.json`
  - `package-lock.json`
  - `android/app/build.gradle` (`versionName`)
- Increment Android `versionCode` by 1.
- Write updated text files in UTF-8 **without BOM**.
- Fail fast if invalid text bytes/chars are detected.

## Commit Template

Use repository `.gitmessage` structure exactly:

```text
<type>(<scope>): <中文摘要> | <English summary>

- 类型(Type): <feat|service|refactor|ui|docs，多个用逗号分隔>
- 功能(Feature): <内容> 或 N/A
- 服务(Service): <内容> 或 N/A
- 重构(Refactor): <内容> 或 N/A
- 交互与界面(UI/UX): <内容> 或 N/A
- 文档(Docs): <内容> 或 N/A

- Types: <feat|service|refactor|ui|docs, comma-separated>
- Feature: <content> or N/A
- Service: <content> or N/A
- Refactor: <content> or N/A
- UI/UX: <content> or N/A
- Docs: <content> or N/A
```

## Local Commit Execution Rule

- Save commit message in UTF-8 without BOM.
- Run wrapper script:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/commit-local-with-check.ps1 -MessageFile .git/COMMIT_MSG.txt
```

The script enforces:

- commit template structure
- commit message encoding
- no BOM in staged text files
- no replacement character `U+FFFD` in staged text files
- no disallowed control characters in staged text files

## Release Tag Rule

- Required format: `vX.0.Y`.
- Prefer ASCII-only annotation text to minimize encoding issues in external tooling.
- Tag should match the version already written into release files.

## Remote Command Rule

Use auth wrapper for every remote operation:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 push origin main
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 push origin <tag>
```

## Encoding Verification

After commit, verify Chinese displays correctly:

```powershell
git log -1 --pretty=%B
```

If text is garbled, fix encoding locally and recommit before pushing.
