[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory = $true)]
    [string]$MessageFile,
    [switch]$StageAll = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (git rev-parse --show-toplevel).Trim()
if (-not $repoRoot) {
    throw "Failed to resolve repository root."
}

$delegateScript = Join-Path $repoRoot ".codex/skills/version-release/scripts/commit-local-with-check.ps1"
if (-not (Test-Path -LiteralPath $delegateScript)) {
    throw "Delegate script not found: $delegateScript"
}

$args = @("-ExecutionPolicy", "Bypass", "-File", $delegateScript, "-MessageFile", $MessageFile)
if ($StageAll) {
    $args += "-StageAll"
}

& powershell @args
exit $LASTEXITCODE
