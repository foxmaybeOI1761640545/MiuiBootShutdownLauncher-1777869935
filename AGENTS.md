# Repository Instructions

## Persistent Install Guard

The `command-install-guard` skill is a default guardrail for this repository.

For every shell command Codex runs in this repo:

- If the command fails and the failure may be caused by a missing environment, program, CLI, runtime, package, plugin, SDK, wrapper distribution, or dependency, Codex must automatically follow `.codex/skills/command-install-guard/SKILL.md`.
- This applies without the user manually mentioning or loading the skill.
- Codex must inspect the failed command, run only safe non-mutating presence/version checks, report the missing requirement, install scope, disk-space estimate, and exact `ALLOW_INSTALL ...` authorization line, then stop.
- Codex must not install, download, bootstrap, sync, update, or retry commands that would implicitly install missing tools or dependencies unless the user sends a complete standalone `ALLOW_INSTALL ...` command.
- This guard covers commands Codex runs. It does not intercept commands the user runs independently outside Codex unless the user shares the failure output.
