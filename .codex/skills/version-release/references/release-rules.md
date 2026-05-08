# Release Rules

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

## Release Tag Rule

- Required format: `vx.0.y`
- `x`: current major version from the highest matching existing tag
- `y`: previous highest `y` + 1
- Use `scripts/next-release-tag.ps1` to calculate the next tag

## Push Strategy

1. Push commit(s) to branch first.
2. Create and verify annotated tag.
3. Push tag to remote.

## Encoding Check

After commit, verify Chinese displays correctly:

```powershell
git log -1 --pretty=%B
```

If text is garbled, enforce UTF-8 in repo-level git config before retrying.
