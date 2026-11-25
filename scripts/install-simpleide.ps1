# Downloads the best published Simple IDE package for this Windows system.
#
# This script only validates the host, finds the latest GitHub release and
# downloads one .zip asset into the current directory (or SIMPLEIDE_OUTPUT_DIR).
# It never extracts, installs or executes the downloaded package.
#
# Selection order: javafx23 -> javafx22 -> nojavafx, when such a package was
# published for the detected architecture. Windows x86 and ARM64 only have
# nojavafx packages. Windows 10 is required on x86/x64 and Windows 11 on ARM64.
#
# Usage:
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-simpleide.ps1
#   powershell ... -File scripts/install-simpleide.ps1 -OutputDir "$env:USERPROFILE\Downloads"
#   powershell ... -File scripts/install-simpleide.ps1 -Tag v1.1.0 -Force
#
# Environment equivalents:
#
#   SIMPLEIDE_TAG         Use a specific release tag instead of the latest one.
#   SIMPLEIDE_OUTPUT_DIR  Destination directory (default: current directory).
#   SIMPLEIDE_FORCE       Set to 1 to overwrite an existing file.
#
# The script is documented in docs/reference/INSTALL.md.

param(
    [switch] $Help,
    [string] $Tag = $env:SIMPLEIDE_TAG,
    [string] $OutputDir = $env:SIMPLEIDE_OUTPUT_DIR,
    [switch] $Force
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repository = "BrunoRNS/SIMPLE-IDE"
$apiUrl = "https://api.github.com/repos/$repository/releases/latest"
$releasesUrl = "https://github.com/$repository/releases/download"
$headers = @{ "User-Agent" = "install-simpleide.ps1" }

function Show-Usage {
    @"
Usage: install-simpleide.ps1 [-Tag <tag>] [-OutputDir <path>] [-Force] [-Help]

Downloads the newest Simple IDE zip package that fits this Windows system. The
file is left in the output directory; it is not extracted, installed or run.

Parameters:
  -Tag <tag>          Release tag to use (default: latest GitHub release).
  -OutputDir <path>   Destination directory (default: current directory).
  -Force              Overwrite an existing downloaded file.
  -Help               Show this help and exit.
"@
}

if ($Help) {
    Show-Usage
    return
}

if (-not $Force -and $env:SIMPLEIDE_FORCE -eq "1") {
    $Force = $true
}

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
} catch {
    # PowerShell 7 already negotiates TLS 1.2 or newer.
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = (Get-Location).Path
}
if (-not (Test-Path -LiteralPath $OutputDir -PathType Container)) {
    throw "Output directory does not exist: $OutputDir"
}

$architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
switch ($architecture) {
    ([System.Runtime.InteropServices.Architecture]::X64) { $arch = "x64" }
    ([System.Runtime.InteropServices.Architecture]::X86) { $arch = "x86" }
    ([System.Runtime.InteropServices.Architecture]::Arm64) { $arch = "arm64" }
    default { throw "Unsupported Windows architecture: $architecture" }
}

$osVersion = [Environment]::OSVersion.Version
if ($arch -eq "arm64") {
    # Windows 11 starts at build 22000; Windows 10 ARM64 is not supported.
    if ($osVersion.Major -lt 10 -or $osVersion.Build -lt 22000) {
        throw "Simple IDE ARM64 requires Windows 11 (build 22000+); found $osVersion"
    }
} elseif ($osVersion.Major -lt 10) {
    throw "Simple IDE requires Windows 10 or newer; found $osVersion"
}

if ([string]::IsNullOrWhiteSpace($Tag)) {
    $release = Invoke-RestMethod -Uri $apiUrl -Headers $headers -TimeoutSec 30
    $Tag = $release.tag_name
}
if ([string]::IsNullOrWhiteSpace($Tag) -or $Tag -notmatch '^v[A-Za-z0-9._+\-]+$') {
    throw "Unexpected release tag '$Tag' (expected a leading 'v')"
}
$version = $Tag.Substring(1)

$variants = @("nojavafx")
if ($arch -eq "x64") {
    $variants = @("javafx23", "javafx22", "nojavafx")
}

Write-Host "Repository: $repository"
Write-Host "Release: $Tag"
Write-Host "System: windows-$arch (Windows $osVersion)"

$base = "simpleide-v$version-windows-$arch"
$asset = $null
$assetUrl = $null

function Test-AssetUrl([string] $url) {
    try {
        $response = Invoke-WebRequest -Uri $url -Method Head -Headers $headers `
            -MaximumRedirection 5 -TimeoutSec 30 -UseBasicParsing -ErrorAction Stop
        return [int] $response.StatusCode -lt 400
    } catch {
        return $false
    }
}

foreach ($variant in $variants) {
    $name = "$base-$variant.zip"
    $url = "$releasesUrl/$Tag/$name"
    if (Test-AssetUrl $url) {
        $asset = $name
        $assetUrl = $url
        break
    }
    Write-Host "Not published: $name"
}

if ($null -eq $asset) {
    throw "No compatible package was published in release $Tag (tried: $($variants -join ', '))"
}

$destination = Join-Path $OutputDir $asset
if ((Test-Path -LiteralPath $destination) -and -not $Force) {
    throw "$destination already exists; move it or pass -Force to overwrite"
}

$tempFile = Join-Path $OutputDir (".simpleide-download." + [Guid]::NewGuid().ToString("N"))
try {
    Write-Host "Downloading: $assetUrl"
    Invoke-WebRequest -Uri $assetUrl -OutFile $tempFile -Headers $headers `
        -MaximumRedirection 5 -TimeoutSec 120 -UseBasicParsing

    $item = Get-Item -LiteralPath $tempFile
    if ($item.Length -le 0) {
        throw "The downloaded file is empty: $asset"
    }
    Move-Item -LiteralPath $tempFile -Destination $destination -Force
} finally {
    if (Test-Path -LiteralPath $tempFile) {
        Remove-Item -LiteralPath $tempFile -Force
    }
}

Write-Host "Saved: $destination"
Write-Host "The archive was not extracted, installed or executed."
