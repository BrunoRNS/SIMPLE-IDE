# Validates the packages produced by scripts/package.ps1 for the current
# OS/architecture. Used by CI after `make pkg` so a broken executable never
# reaches a release unnoticed.
#
# Checks:
#   .zip  the portable app image extracts and contains the launcher .exe
#   .msi  the Windows Installer database can be opened and lists files
#
# Exits non-zero when a package is missing or invalid.

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$properties = Join-Path $rootDir "simpleide\src\main\resources\simpleide.properties"
$pkgDir = Join-Path $rootDir "simpleide\target\pkg"

$version = (Get-Content $properties | Where-Object { $_ -match '^version=' } | Select-Object -First 1) -replace '^version=', ''
$version = $version.Trim()
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "Unable to read the application version from $properties"
}

$os = if ($env:SIMPLEIDE_PKG_OS) { $env:SIMPLEIDE_PKG_OS } else { "windows" }
$arch = if ($env:SIMPLEIDE_PKG_ARCH) { $env:SIMPLEIDE_PKG_ARCH } else { "x64" }
$artifactBase = "simpleide-v$version-$os-$arch"

function Fail([string]$message) {
    Write-Error "PACKAGE VALIDATION FAILED: $message"
    exit 1
}

function Ok([string]$message) {
    Write-Host "VALID: $message"
}

if (-not (Test-Path $pkgDir)) {
    Fail "The package directory was not found: $pkgDir"
}

$archive = Join-Path $pkgDir "$artifactBase.zip"
if (-not (Test-Path $archive)) {
    Fail "$archive was not produced."
}

$extractDir = Join-Path $pkgDir "validate-zip"
if (Test-Path $extractDir) {
    Remove-Item -Recurse -Force $extractDir
}
New-Item -ItemType Directory -Path $extractDir -Force | Out-Null
try {
    Expand-Archive -LiteralPath $archive -DestinationPath $extractDir -Force
} catch {
    Fail "The app image zip is not a readable zip: $archive ($($_.Exception.Message))"
}

$launcher = Get-ChildItem -Path $extractDir -Filter "SimpleIDE.exe" -Recurse | Select-Object -First 1
if ($null -eq $launcher) {
    Fail "The app image zip has no SimpleIDE.exe launcher: $archive"
}
if ($launcher.Length -lt 1MB) {
    Fail "The launcher looks too small to be a jpackage executable: $($launcher.FullName)"
}

$msi = Join-Path $pkgDir "$artifactBase.msi"
if (-not (Test-Path $msi)) {
    Fail "$msi was not produced."
}
try {
    $windowsInstaller = New-Object -ComObject WindowsInstaller.Installer
    $database = $windowsInstaller.GetType().InvokeMember(
        "OpenDatabase",
        "InvokeMethod",
        $null,
        $windowsInstaller,
        @($msi, 0))
    $view = $database.GetType().InvokeMember(
        "OpenView",
        "InvokeMethod",
        $null,
        $database,
        @("SELECT ``Value`` FROM ``Property`` WHERE ``Property`` = 'ProductName'"))
    $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null) | Out-Null
    $record = $view.GetType().InvokeMember("Fetch", "InvokeMethod", $null, $view, $null)
    $productName = $record.GetType().InvokeMember("StringData", "GetProperty", $null, $record, @(1))
    if ($productName -notmatch "SimpleIDE") {
        Fail "$msi does not declare the SimpleIDE product name (got '$productName')."
    }
} catch {
    Fail "Windows Installer cannot read $msi ($($_.Exception.Message))"
}

Remove-Item -Recurse -Force $extractDir

Ok $archive
Ok $msi
Write-Host "All packages for $os-$arch are valid."
exit 0
