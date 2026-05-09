[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$NextTag,
    [string]$Pattern = "v*.0.*",
    [switch]$SkipNpmVersion,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:CurrentScriptDir = Split-Path -Parent $PSCommandPath

function Resolve-RepoRoot {
    $root = git rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        throw "Failed to resolve repository root."
    }
    return $root.Trim()
}

function Get-NextTag {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,
        [Parameter(Mandatory = $true)]
        [string]$TagPattern
    )

    if (-not [string]::IsNullOrWhiteSpace($NextTag)) {
        return $NextTag.Trim()
    }

    $nextTagScript = Join-Path $script:CurrentScriptDir "next-release-tag.ps1"
    $raw = & powershell -ExecutionPolicy Bypass -File $nextTagScript -Pattern $TagPattern
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($raw -join ""))) {
        throw "Failed to compute next release tag."
    }
    return (($raw -join "`n").Trim())
}

function Parse-TagToVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    if ($Tag -notmatch "^v(\d+)\.0\.(\d+)$") {
        throw "Tag '$Tag' does not match required format vX.0.Y."
    }

    return [PSCustomObject]@{
        Major   = [int]$Matches[1]
        Patch   = [int]$Matches[2]
        Version = "$($Matches[1]).0.$($Matches[2])"
    }
}

function Read-TextNoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )
    return [System.IO.File]::ReadAllText($Path)
}

function Write-TextNoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Text
    )
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Get-VersionCode {
    param(
        [Parameter(Mandatory = $true)]
        [string]$GradleText
    )
    $match = [regex]::Match($GradleText, "(?m)^(\s*versionCode\s+)(\d+)\s*$")
    if (-not $match.Success) {
        throw "Cannot find versionCode in android/app/build.gradle."
    }
    return [int]$match.Groups[2].Value
}

function Ensure-NoIllegalTextChars {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $text = Read-TextNoBom -Path $Path

    if ($text.IndexOf([char]0xFEFF) -ge 0) {
        throw "File contains UTF-8 BOM marker inside text: $Path"
    }
    if ($text.IndexOf([char]0xFFFD) -ge 0) {
        throw "File contains replacement character U+FFFD: $Path"
    }
    if ([regex]::IsMatch($text, "[\x00-\x08\x0B\x0C\x0E-\x1F]")) {
        throw "File contains disallowed control characters: $Path"
    }
}

$repoRoot = Resolve-RepoRoot
$resolvedTag = Get-NextTag -RepoRoot $repoRoot -TagPattern $Pattern
$parsedTag = Parse-TagToVersion -Tag $resolvedTag
$nextVersion = $parsedTag.Version

$buildGradlePath = Join-Path $repoRoot "android/app/build.gradle"
if (-not (Test-Path -LiteralPath $buildGradlePath)) {
    throw "Missing file: android/app/build.gradle"
}

$gradleText = Read-TextNoBom -Path $buildGradlePath
$currentVersionCode = Get-VersionCode -GradleText $gradleText
$nextVersionCode = $currentVersionCode + 1

if (-not $SkipNpmVersion -and -not $DryRun) {
    Push-Location $repoRoot
    try {
        & npm version $nextVersion --no-git-tag-version
        if ($LASTEXITCODE -ne 0) {
            throw "npm version failed while setting version to $nextVersion."
        }
    } finally {
        Pop-Location
    }
}

$updatedGradle = $gradleText
$updatedGradle = [regex]::Replace(
    $updatedGradle,
    '(?m)^(\s*versionCode\s+)\d+\s*$',
    '${1}' + $nextVersionCode,
    1
)
$updatedGradle = [regex]::Replace(
    $updatedGradle,
    '(?m)^(\s*versionName\s+")\d+\.\d+\.\d+(")\s*$',
    '${1}' + $nextVersion + '${2}',
    1
)
if (-not $DryRun) {
    Write-TextNoBom -Path $buildGradlePath -Text $updatedGradle
}

$packageJsonPath = Join-Path $repoRoot "package.json"
$packageLockPath = Join-Path $repoRoot "package-lock.json"
foreach ($path in @($packageJsonPath, $packageLockPath, $buildGradlePath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing file: $path"
    }
    if (-not $DryRun) {
        Ensure-NoIllegalTextChars -Path $path
    }
}

if (-not $DryRun) {
    $packageJsonText = Get-Content -Raw -LiteralPath $packageJsonPath -Encoding UTF8
    $packageLockText = Get-Content -Raw -LiteralPath $packageLockPath -Encoding UTF8
    $gradleVerify = Read-TextNoBom -Path $buildGradlePath

    $pkgVersionPattern = '(?m)^\s*"version"\s*:\s*"' + [regex]::Escape($nextVersion) + '"\s*,?\s*$'
    if ($packageJsonText -notmatch $pkgVersionPattern) {
        throw "package.json version mismatch. expected=$nextVersion"
    }

    $lockTopVersionMatch = [regex]::Match($packageLockText, '(?ms)^\s*\{\s*"name"\s*:\s*"[^"]*"\s*,\s*"version"\s*:\s*"([^"]+)"')
    if (-not $lockTopVersionMatch.Success) {
        throw "package-lock.json top-level version field not found."
    }
    if ($lockTopVersionMatch.Groups[1].Value -ne $nextVersion) {
        throw "package-lock.json version mismatch. expected=$nextVersion actual=$($lockTopVersionMatch.Groups[1].Value)"
    }

    $lockRootPkgMatch = [regex]::Match(
        $packageLockText,
        '(?ms)"packages"\s*:\s*\{\s*""\s*:\s*\{[^{}]*?"version"\s*:\s*"([^"]+)"'
    )
    if (-not $lockRootPkgMatch.Success) {
        throw "package-lock.json packages.\"\".version field not found."
    }
    if ($lockRootPkgMatch.Groups[1].Value -ne $nextVersion) {
        throw "package-lock.json root package version mismatch. expected=$nextVersion actual=$($lockRootPkgMatch.Groups[1].Value)"
    }
    $versionNamePattern = '(?m)^\s*versionName\s+"' + [regex]::Escape($nextVersion) + '"\s*$'
    if ($gradleVerify -notmatch $versionNamePattern) {
        throw "build.gradle versionName mismatch for $nextVersion"
    }
}

Write-Output "next_tag=$resolvedTag"
Write-Output "next_version=$nextVersion"
Write-Output "next_version_code=$nextVersionCode"
Write-Output "updated_files=package.json,package-lock.json,android/app/build.gradle"
Write-Output "dry_run=$DryRun"
