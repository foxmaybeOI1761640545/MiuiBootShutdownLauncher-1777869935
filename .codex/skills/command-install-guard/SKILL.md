---
name: command-install-guard
description: Safety workflow for any failed shell/terminal command that may indicate a missing environment, program, CLI, runtime, package, plugin, SDK, or dependency. Use after a command fails before retrying, installing, downloading, syncing, or running a command that could implicitly install new tools or dependencies; enforce version checks, disk-space estimates, install-scope reporting, and explicit user installation authorization.
---

# Command Install Guard

## Purpose

Use this skill after any command fails and the failure could be caused by a missing environment, program, dependency, SDK, runtime, CLI, package, plugin, or downloaded tool distribution.

The goal is to keep work moving while preventing accidental installs. A failed command is a checkpoint: verify what exists, report what is missing, and never install anything new unless the user separately sends the complete approval command defined below.

## Core Rule

Never install, download, bootstrap, sync, or update a new environment/program/dependency after a command failure unless the user sends a standalone, complete `ALLOW_INSTALL` command.

Do not treat these as approval:

- `yes`
- `ok`
- `continue`
- `install it`
- `go ahead`
- Any partial approval missing the exact command, scope, or disk estimate

## Failure Workflow

1. Capture the failed command, exit code, and the relevant error lines.
2. Decide whether the failure might be caused by a missing tool or dependency. If uncertain, assume it might be missing and run the guard.
3. Verify installation with a non-mutating version check:
   - Prefer `<program> --version`.
   - If `--version` is known to trigger downloads or bootstrapping, do not run it; use a non-installing presence check such as `Get-Command`, `where.exe`, `Test-Path`, `npm list`, `pip show`, or reading lock/config files.
   - If `--version` fails because the executable is missing/not recognized, classify it as not installed.
   - If `--version` fails because the program exists but does not support that flag, use one safe non-installing presence check before deciding.
4. If the requirement is installed, do not propose installation. Diagnose the original failure normally.
5. If the requirement is not installed, do not retry the original command when it would require the missing item or implicitly download it.
6. Tell the user:
   - What command failed.
   - What environment/program/dependency appears missing.
   - Which check was run and what it showed.
   - Whether installation would be global or project-local.
   - The minimum disk space the user should prepare.
   - The exact `ALLOW_INSTALL` command they may copy if they want Codex to install it.
7. Stop. Wait for the user to send the complete `ALLOW_INSTALL` command as a separate message before installing.

## Install Scope Classification

Classify the proposed install before asking for approval:

- `global`: OS-level tools, runtimes, SDKs, CLIs, package managers, Android SDK/Gradle/JDK, Docker, Git, Node, Python, Java, Rust, Go, Ruby, system build tools.
- `project`: dependencies written into the current repo or virtual environment, such as `npm install`, `pnpm add`, `pip install -r requirements.txt` in a venv, Gradle project dependencies, Cargo crates in the current project, or downloaded wrapper/distribution files inside the workspace.
- `mixed`: both global and project-local changes are expected. Prefer splitting into separate approvals instead of using `mixed`.

If scope is ambiguous, say so and choose the safer classification. Global installs need extra caution.

## Disk Space Estimate

Always provide a conservative minimum. If exact size is unknown, state it as an estimate and round up.

Use current official docs, package manager metadata, existing cache size, or common ecosystem knowledge when available. If none is available, use these minimums:

- Small project package/library: at least `200 MB`
- Typical npm/pnpm/yarn project dependency install: at least `1 GB`
- Python virtualenv dependency set: at least `1 GB`
- Single global CLI/tool: at least `500 MB`
- Language runtime or SDK such as Node, Python, Go, Rust, JDK: at least `2 GB`
- Android SDK/Gradle/JDK/mobile build stack: at least `10 GB`
- Native build tools, Visual Studio Build Tools, Xcode tools, Docker, emulators: at least `20 GB`

When a wrapper would download a distribution, count that as a dependency download and report it as project/local-cache installation. Example: Gradle wrapper downloading a Gradle distribution needs approval if it is not already present.

## Approval Command Format

Only this exact format authorizes installation:

```text
ALLOW_INSTALL name="<environment/program/dependency>" scope="<global|project|mixed>" min_space="<disk space>" command="<exact install command>"
```

Rules:

- The user must send the complete command as a standalone message.
- The `command` field must be the exact command Codex will run.
- Do not run a different command than the one inside `command="..."`.
- If the command needs multiple install steps, provide separate `ALLOW_INSTALL` lines or one command that explicitly chains all steps.
- If the user edits the command, run only the edited command if it is complete and safe.
- If the approval command is incomplete, ask for the complete command and do not install.

## User-Facing Response Template

Use this structure when a missing dependency is detected:

```text
命令 `<failed command>` 失败后，我检查了 `<check command>`，结果显示 `<name>` 未安装或不可用。

我不会继续执行会触发安装/下载的命令。

安装范围：<global|project|mixed>
至少预留磁盘空间：<size>

如果你希望我安装并继续，请单独发送这行完整许可命令：
ALLOW_INSTALL name="<name>" scope="<scope>" min_space="<size>" command="<exact install command>"
```

## After Approval

When the user sends a complete `ALLOW_INSTALL` command:

1. Verify the command is an install/download/bootstrap command matching the named dependency.
2. Re-state what will be installed, scope, and minimum disk space in one short sentence.
3. Run exactly the command inside the `command` field.
4. After installation, run the safe version check again.
5. Only then retry the original failed command, if it is still relevant.

## Non-Install Commands Allowed

The guard permits safe inspection commands without approval, including:

- `<program> --version` when non-mutating
- `Get-Command <program>`
- `where.exe <program>`
- `Test-Path <path>`
- `git status`, `git diff`, `git log`
- Package listing commands such as `npm list --depth=0`, `pip show <pkg>`, `cargo metadata --no-deps` when they do not install
- Reading existing config, lockfiles, logs, or manifests

Do not use package-manager commands that can mutate files or download dependencies unless approved.