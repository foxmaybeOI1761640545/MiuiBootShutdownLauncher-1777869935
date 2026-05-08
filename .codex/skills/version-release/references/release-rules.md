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

## Commit Template

Use the repository `.gitmessage` structure exactly:

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

- Save commit message in UTF-8 (without BOM preferred).
- Run the wrapper script to ensure strict template validation and encoding check:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/commit-local-with-check.ps1 -MessageFile .git/COMMIT_MSG.txt
```

- The script fails if required template sections are missing.
- The script fails if the message stored in the commit does not match the source message text.

## Release Tag Rule

- Required format: `vx.0.y`
- `x`: current major version from the highest matching existing tag
- `y`: previous highest `y` + 1
- Use `scripts/next-release-tag.ps1` to calculate the next tag

## Remote Command Rule

Use the auth wrapper for every remote operation:

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 fetch origin
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 push origin main
powershell -ExecutionPolicy Bypass -File .codex/skills/version-release/scripts/git-auth.ps1 push origin <tag>
```

## Encoding Check

After commit, verify Chinese displays correctly:

```powershell
git log -1 --pretty=%B
```

If text is garbled, enforce UTF-8 in repo-level git config before retrying.
