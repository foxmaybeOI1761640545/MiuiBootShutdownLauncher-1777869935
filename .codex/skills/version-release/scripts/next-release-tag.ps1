param(
    [string]$Pattern = "v*.0.*"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$rawTags = git tag --list $Pattern
if (-not $rawTags) {
    throw "No tags found matching pattern '$Pattern'."
}

$parsedTags = @()
foreach ($tag in $rawTags) {
    if ($tag -match "^v(\d+)\.0\.(\d+)$") {
        $parsedTags += [PSCustomObject]@{
            Tag   = $tag
            Major = [int]$Matches[1]
            Patch = [int]$Matches[2]
        }
    }
}

if (-not $parsedTags) {
    throw "No tags matched strict format vX.0.Y."
}

$latest = $parsedTags | Sort-Object Major, Patch | Select-Object -Last 1
$nextTag = "v{0}.0.{1}" -f $latest.Major, ($latest.Patch + 1)

Write-Output $nextTag
