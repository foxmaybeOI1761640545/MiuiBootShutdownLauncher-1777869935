param(
    [string]$Pattern = "v*.0.*",
    [int]$DefaultMajor = 1,
    [int]$DefaultPatch = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$rawTags = git tag --list $Pattern
$parsedTags = @()
if ($rawTags) {
    foreach ($tag in $rawTags) {
        if ($tag -match "^v(\d+)\.0\.(\d+)$") {
            $parsedTags += [PSCustomObject]@{
                Tag   = $tag
                Major = [int]$Matches[1]
                Patch = [int]$Matches[2]
            }
        }
    }
}

if (-not $parsedTags) {
    $initialTag = "v{0}.0.{1}" -f $DefaultMajor, $DefaultPatch
    Write-Output $initialTag
    exit 0
}

$latest = $parsedTags | Sort-Object Major, Patch | Select-Object -Last 1
$nextTag = "v{0}.0.{1}" -f $latest.Major, ($latest.Patch + 1)

Write-Output $nextTag
