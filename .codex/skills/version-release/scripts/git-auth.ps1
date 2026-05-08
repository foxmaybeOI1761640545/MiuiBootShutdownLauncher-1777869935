[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$AuthFile = ".git/version-release-auth.env",
    [string]$Remote,
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]]$GitArgs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "auth-common.ps1")

$ctx = Get-ReleaseAuthContext -AuthFile $AuthFile -RemoteOverride $Remote
$header = Get-BasicAuthHeader -Username $ctx.Username -Pat $ctx.Pat

$cmdArgs = @("-c", "http.extraheader=$header") + $GitArgs
& git @cmdArgs
exit $LASTEXITCODE
