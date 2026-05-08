# Commit Rules

## Required Template

Use `.gitmessage` exactly:

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

## Local Commit Command

```powershell
powershell -ExecutionPolicy Bypass -File .codex/skills/commit-local/scripts/commit-local.ps1 -MessageFile .git/COMMIT_MSG.txt
```

## Post-Commit Validation

- Confirm Chinese rendering:

```powershell
git log -1 --pretty=%B
```

- If garbled text appears, keep commit local and fix encoding before any push.
