param(
    [string]$AuthFile = ".git/version-release-auth.env",
    [string]$Remote,
    [switch]$Unset
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "auth-common.ps1")

if ($Unset) {
    $repoRoot = Resolve-RepoRoot
    $remoteName = if ($Remote) { $Remote.Trim() } else { "origin" }
    $remoteUrl = git remote get-url $remoteName
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($remoteUrl)) {
        throw "Failed to resolve remote URL for '$remoteName'."
    }
    $remoteUrl = $remoteUrl.Trim()
    $legacyCfgKey = "http.{0}.extraheader" -f $remoteUrl
    git config --local --unset-all $legacyCfgKey 2>$null

    git config --local --unset version-release.auth-file 2>$null
    git config --local --unset version-release.remote 2>$null
    git config --local --unset version-release.remote-url 2>$null
    git config --local --unset version-release.user 2>$null
    git config --local --unset version-release.email 2>$null

    Write-Output "Removed local version-release auth configuration for remote '$remoteName'."
    exit 0
}

$ctx = Get-ReleaseAuthContext -AuthFile $AuthFile -RemoteOverride $Remote
$header = Get-BasicAuthHeader -Username $ctx.Username -Pat $ctx.Pat
$legacyCfgKey = "http.{0}.extraheader" -f $ctx.RemoteUrl
git config --local --unset-all $legacyCfgKey 2>$null

git config --local version-release.auth-file $ctx.AuthFilePath
git config --local version-release.remote $ctx.RemoteName
git config --local version-release.remote-url $ctx.RemoteUrl
git config --local version-release.user $ctx.Username

if ($ctx.Email) {
    git config --local version-release.email $ctx.Email
}

# Sanity check auth context through runtime header injection.
& git -c "http.extraheader=$header" ls-remote --heads $ctx.RemoteName > $null
if ($LASTEXITCODE -ne 0) {
    throw "PAT auth check failed for remote '$($ctx.RemoteName)'."
}

Write-Output "Configured local PAT metadata for remote '$($ctx.RemoteName)'."
Write-Output "Auth file is local-only: $($ctx.AuthFilePath)"
