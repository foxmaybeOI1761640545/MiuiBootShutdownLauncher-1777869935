[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory = $true)]
    [string]$MessageFile,
    [switch]$StageAll = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $root = git rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        throw "Failed to resolve repository root."
    }
    return $root.Trim()
}

function Resolve-PathInRepo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PathText,
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    if ([System.IO.Path]::IsPathRooted($PathText)) {
        return $PathText
    }

    return (Join-Path $RepoRoot $PathText)
}

function Validate-CommitMessage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    if ([string]::IsNullOrWhiteSpace($Content)) {
        throw "Commit message is empty."
    }

    $lines = $Content -split "\r?\n"
    $subject = $lines[0].Trim()
    if ($subject -notmatch '^[^:\r\n]+\([^)]+\):\s+.+\s+\|\s+.+$') {
        throw "Invalid subject. Expected '<type>(<scope>): <中文摘要> | <English summary>'."
    }

    $requiredPatterns = @(
        @{ Key = "CN-Type";     Pattern = "(?m)^-\s+.*\(Type\):" },
        @{ Key = "CN-Feature";  Pattern = "(?m)^-\s+.*\(Feature\):" },
        @{ Key = "CN-Service";  Pattern = "(?m)^-\s+.*\(Service\):" },
        @{ Key = "CN-Refactor"; Pattern = "(?m)^-\s+.*\(Refactor\):" },
        @{ Key = "CN-UIUX";     Pattern = "(?m)^-\s+.*\(UI/UX\):" },
        @{ Key = "CN-Docs";     Pattern = "(?m)^-\s+.*\(Docs\):" },
        @{ Key = "EN-Types";    Pattern = "(?m)^-\s+Types:" },
        @{ Key = "EN-Feature";  Pattern = "(?m)^-\s+Feature:" },
        @{ Key = "EN-Service";  Pattern = "(?m)^-\s+Service:" },
        @{ Key = "EN-Refactor"; Pattern = "(?m)^-\s+Refactor:" },
        @{ Key = "EN-UIUX";     Pattern = "(?m)^-\s+UI/UX:" },
        @{ Key = "EN-Docs";     Pattern = "(?m)^-\s+Docs:" }
    )

    $missing = @()
    foreach ($entry in $requiredPatterns) {
        if ($Content -notmatch $entry.Pattern) {
            $missing += $entry.Key
        }
    }

    if ($missing.Count -gt 0) {
        throw ("Commit message missing required sections: " + ($missing -join ", "))
    }
}

$repoRoot = Resolve-RepoRoot
$messagePath = Resolve-PathInRepo -PathText $MessageFile -RepoRoot $repoRoot

if (-not (Test-Path -LiteralPath $messagePath)) {
    throw "Message file not found: $messagePath"
}

git config i18n.commitEncoding utf-8
git config i18n.logOutputEncoding utf-8

$messageContent = Get-Content -Raw -LiteralPath $messagePath -Encoding UTF8
Validate-CommitMessage -Content $messageContent

if ($StageAll) {
    git add -A
}

$staged = git diff --cached --name-only
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read staged changes."
}
if ([string]::IsNullOrWhiteSpace(($staged -join ""))) {
    throw "No staged changes found. Nothing to commit."
}

git commit -F $messagePath
if ($LASTEXITCODE -ne 0) {
    throw "git commit failed."
}

$actual = git log -1 --pretty=%B
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read latest commit message."
}

$expectedNormalized = $messageContent.Replace("`r`n", "`n").TrimEnd()
$actualNormalized = $actual.Replace("`r`n", "`n").TrimEnd()

if ($expectedNormalized -ne $actualNormalized) {
    throw "Commit message mismatch after commit. Potential encoding issue detected."
}

Write-Output "Local commit completed and message encoding check passed."
