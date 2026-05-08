Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $root = git rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        throw "Failed to resolve repository root."
    }
    return $root.Trim()
}

function Resolve-AuthFilePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AuthFile
    )

    $repoRoot = Resolve-RepoRoot
    if ([System.IO.Path]::IsPathRooted($AuthFile)) {
        return $AuthFile
    }
    return (Join-Path $repoRoot $AuthFile)
}

function Parse-AuthFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Auth file not found: $Path"
    }

    $map = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) {
            throw "Invalid line in auth file: '$line'"
        }

        $key = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim()

        if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $map[$key] = $value
    }

    return $map
}

function Get-ReleaseAuthContext {
    param(
        [string]$AuthFile = ".git/version-release-auth.env",
        [string]$RemoteOverride
    )

    $resolvedAuthFile = Resolve-AuthFilePath -AuthFile $AuthFile
    $entries = Parse-AuthFile -Path $resolvedAuthFile

    foreach ($required in @("GITHUB_USER", "GITHUB_PAT")) {
        if (-not $entries.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($entries[$required])) {
            throw "Missing required key '$required' in auth file: $resolvedAuthFile"
        }
    }

    $remoteName = if ($RemoteOverride) { $RemoteOverride } elseif ($entries.ContainsKey("GITHUB_REMOTE")) { $entries["GITHUB_REMOTE"] } else { "origin" }
    $remoteName = $remoteName.Trim()
    if (-not $remoteName) {
        throw "Remote name resolved to empty value."
    }

    $remoteUrl = git remote get-url $remoteName
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($remoteUrl)) {
        throw "Failed to resolve remote URL for '$remoteName'."
    }
    $remoteUrl = $remoteUrl.Trim()

    if (-not $remoteUrl.StartsWith("https://", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Remote '$remoteName' must use https URL for PAT auth. Current URL: $remoteUrl"
    }

    $username = $entries["GITHUB_USER"].Trim()
    $pat = $entries["GITHUB_PAT"].Trim()
    $email = if ($entries.ContainsKey("GITHUB_EMAIL")) { $entries["GITHUB_EMAIL"].Trim() } else { "" }

    return [PSCustomObject]@{
        AuthFilePath = $resolvedAuthFile
        RemoteName   = $remoteName
        RemoteUrl    = $remoteUrl
        Username     = $username
        Pat          = $pat
        Email        = $email
    }
}

function Get-BasicAuthHeader {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Pat
    )

    $encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$Username`:$Pat"))
    return "AUTHORIZATION: basic $encoded"
}
