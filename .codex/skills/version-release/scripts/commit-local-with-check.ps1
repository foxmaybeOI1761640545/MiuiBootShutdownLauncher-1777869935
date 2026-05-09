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

function Validate-MessageEncoding {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "Commit message file must be UTF-8 without BOM: $Path"
    }
}

function Is-TextLikePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $lower = $Path.ToLowerInvariant()
    $extensions = @(
        ".md", ".txt", ".json", ".yml", ".yaml", ".xml",
        ".gradle", ".properties", ".ps1", ".ts", ".tsx", ".js", ".vue",
        ".css", ".scss", ".kt", ".java", ".sh"
    )

    foreach ($ext in $extensions) {
        if ($lower.EndsWith($ext)) {
            return $true
        }
    }
    return $false
}

function Validate-StagedTextFiles {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string[]]$StagedPaths
    )

    foreach ($relativePath in $StagedPaths) {
        if (-not (Is-TextLikePath -Path $relativePath)) {
            continue
        }

        $absolutePath = Join-Path $RepoRoot $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath)) {
            continue
        }

        $bytes = [System.IO.File]::ReadAllBytes($absolutePath)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            throw "Staged text file contains UTF-8 BOM: $relativePath"
        }

        $content = [System.IO.File]::ReadAllText($absolutePath)
        if ($content.IndexOf([char]0xFFFD) -ge 0) {
            throw "Staged text file contains replacement character U+FFFD: $relativePath"
        }
        if ([regex]::IsMatch($content, "[\x00-\x08\x0B\x0C\x0E-\x1F]")) {
            throw "Staged text file contains invalid control characters: $relativePath"
        }
    }
}

$repoRoot = Resolve-RepoRoot
$messagePath = Resolve-PathInRepo -PathText $MessageFile -RepoRoot $repoRoot

if (-not (Test-Path -LiteralPath $messagePath)) {
    throw "Message file not found: $messagePath"
}

git config i18n.commitEncoding utf-8
git config i18n.logOutputEncoding utf-8

Validate-MessageEncoding -Path $messagePath
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

Validate-StagedTextFiles -RepoRoot $repoRoot -StagedPaths $staged

git commit -F $messagePath
if ($LASTEXITCODE -ne 0) {
    throw "git commit failed."
}

$actualLines = git log -1 --pretty=%B
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read latest commit message."
}
$actual = ($actualLines -join "`n")

$expectedNormalized = $messageContent.Replace("`r`n", "`n").TrimEnd()
$actualNormalized = $actual.Replace("`r`n", "`n").TrimEnd()

if ($expectedNormalized -ne $actualNormalized) {
    throw "Commit message mismatch after commit. Potential encoding issue detected."
}

Write-Output "Local commit completed and message encoding check passed."
