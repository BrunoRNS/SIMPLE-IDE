# Validates packages produced by scripts/package.ps1.
#
# Checks:
#   ZIP:
#     - Archive can be extracted.
#     - SimpleIDE.exe exists.
#     - Launcher is a valid PE executable.
#     - Launcher architecture matches the requested architecture.
#     - Bundled JVM exists and has the expected architecture.
#
#   MSI:
#     - Windows Installer database can be opened.
#     - ProductName identifies SimpleIDE.
#     - Summary Information platform matches the requested architecture.
#
# Exits non-zero when a package is missing or invalid.

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir

$properties = Join-Path $rootDir "simpleide\src\main\resources\simpleide.properties"
$pkgDir = Join-Path $rootDir "simpleide\target\pkg"

if (-not (Test-Path $properties)) {
    throw "Application properties not found: $properties"
}

if (-not (Test-Path $pkgDir)) {
    throw "Package directory not found: $pkgDir"
}

$versionLine = Get-Content $properties |
    Where-Object { $_ -match '^version=' } |
    Select-Object -First 1

if (-not $versionLine) {
    throw "Unable to read application version from $properties"
}

$version = ($versionLine -replace '^version=', '').Trim()

if ([string]::IsNullOrWhiteSpace($version)) {
    throw "Application version is empty."
}

$os = if ($env:SIMPLEIDE_PKG_OS) {
    $env:SIMPLEIDE_PKG_OS
} else {
    "windows"
}

$arch = if ($env:SIMPLEIDE_PKG_ARCH) {
    $env:SIMPLEIDE_PKG_ARCH
} else {
    "x64"
}

$artifactBase = "simpleide-v$version-$os-$arch"

function Fail([string]$message) {
    throw "PACKAGE VALIDATION FAILED: $message"
}

function Ok([string]$message) {
    Write-Host "VALID: $message" -ForegroundColor Green
}

# --------------------------------------------------
# Architecture configuration
# --------------------------------------------------

$expectedMachine = switch ($arch) {
    "x86"   { 0x014C }
    "x64"   { 0x8664 }
    "arm64" { 0xAA64 }
    default {
        Fail "Unsupported architecture: $arch"
    }
}

# --------------------------------------------------
# PE executable architecture detection
# --------------------------------------------------

function Get-PeMachine([string]$path) {

    if (-not (Test-Path -LiteralPath $path)) {
        Fail "Executable not found: $path"
    }

    $stream = [System.IO.File]::OpenRead($path)

    try {
        if ($stream.Length -lt 64) {
            Fail "File is too small to be a valid PE executable: $path"
        }

        $reader = [System.IO.BinaryReader]::new($stream)

        # DOS signature: MZ
        $dosSignature = $reader.ReadUInt16()

        if ($dosSignature -ne 0x5A4D) {
            Fail "Invalid DOS signature: $path"
        }

        # Offset of PE header.
        $stream.Position = 0x3C
        $peOffset = $reader.ReadUInt32()

        if ($peOffset -gt ($stream.Length - 6)) {
            Fail "Invalid PE header offset: $path"
        }

        $stream.Position = $peOffset

        # PE signature.
        $peSignature = $reader.ReadUInt32()

        if ($peSignature -ne 0x00004550) {
            Fail "Invalid PE signature: $path"
        }

        # IMAGE_FILE_HEADER.Machine
        return [int]$reader.ReadUInt16()
    }
    finally {
        $stream.Dispose()
    }
}

function Assert-PeArchitecture(
    [string]$path,
    [string]$description
) {

    $machine = Get-PeMachine $path

    if ($machine -ne $expectedMachine) {
        $actual = "0x{0:X4}" -f $machine
        $expected = "0x{0:X4}" -f $expectedMachine

        Fail "$description has architecture $actual; expected $arch ($expected): $path"
    }

    $size = (Get-Item -LiteralPath $path).Length

    Ok "$description is valid $arch ($size bytes)"
}

# --------------------------------------------------
# ZIP validation
# --------------------------------------------------

$archive = Join-Path $pkgDir "$artifactBase.zip"

if (-not (Test-Path $archive)) {
    Fail "ZIP package was not produced: $archive"
}

$extractDir = Join-Path $pkgDir "validate-zip"

if (Test-Path $extractDir) {
    Remove-Item -Recurse -Force $extractDir
}

# Windows PowerShell 5.1 runs on .NET Framework, whose ZipFile type has no
# three-argument ExtractToDirectory overload. Expand-Archive -Force uses that
# overload, so call the two-argument API on a fresh directory instead.
if (-not ("System.IO.Compression.ZipFile" -as [type])) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
}

try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($archive, $extractDir)
}
catch {
    Fail "The ZIP archive is invalid: $($_.Exception.Message)"
}

Ok "ZIP archive extracted successfully"

# --------------------------------------------------
# Launcher validation
# --------------------------------------------------

$launcher = Get-ChildItem `
    -Path $extractDir `
    -Filter "SimpleIDE.exe" `
    -File `
    -Recurse |
    Select-Object -First 1

if ($null -eq $launcher) {
    Fail "The ZIP archive does not contain SimpleIDE.exe."
}

Assert-PeArchitecture `
    -path $launcher.FullName `
    -description "SimpleIDE launcher"

# --------------------------------------------------
# JVM validation
# --------------------------------------------------

$appImage = $launcher.Directory.FullName

$jvm = Join-Path `
    $appImage `
    "runtime\bin\server\jvm.dll"

if (-not (Test-Path $jvm)) {

    # Fallback for alternative runtime layouts.
    $jvmFile = Get-ChildItem `
        -Path $appImage `
        -Filter "jvm.dll" `
        -File `
        -Recurse |
        Select-Object -First 1

    if ($null -eq $jvmFile) {
        Fail "Bundled JVM was not found inside the application image."
    }

    $jvm = $jvmFile.FullName
}

Assert-PeArchitecture `
    -path $jvm `
    -description "Bundled JVM"

# --------------------------------------------------
# Runtime structure validation
# --------------------------------------------------

$runtimeBin = Join-Path $appImage "runtime\bin"

if (-not (Test-Path $runtimeBin)) {
    Fail "Bundled Java runtime directory is missing: $runtimeBin"
}

# jpackage strips the runtime's native commands (java.exe) by default. The
# runtime library and the server VM are the architecture-specific pieces that
# remain in the bundled image.
$javaLibrary = Join-Path $runtimeBin "java.dll"

if (-not (Test-Path $javaLibrary)) {
    Fail "Bundled Java runtime library is missing: $javaLibrary"
}

Assert-PeArchitecture `
    -path $javaLibrary `
    -description "Bundled Java runtime library"

Ok "Application image validation completed"

# --------------------------------------------------
# MSI validation
# --------------------------------------------------

$msi = Join-Path $pkgDir "$artifactBase.msi"

if (-not (Test-Path $msi)) {
    Fail "MSI package was not produced: $msi"
}

# Windows Installer automation is an out-of-process COM server. Calling its
# members directly lets PowerShell perform the IDispatch conversion; routing
# the same calls through GetType().InvokeMember() makes the binder reject some
# of the arguments (DISP_E_TYPEMISMATCH) even for valid packages.
try {
    $windowsInstaller = New-Object -ComObject WindowsInstaller.Installer
}
catch {
    Fail "Could not create the Windows Installer COM object ($($_.Exception.Message))"
}

try {
    $database = $windowsInstaller.OpenDatabase($msi, 0)
}
catch {
    Fail "Windows Installer cannot open $msi ($($_.Exception.Message))"
}

try {
    $query = 'SELECT `Value` FROM `Property` WHERE `Property` = ''ProductName'''
    $view = $database.OpenView($query)
    $view.Execute()

    $record = $view.Fetch()

    if ($null -eq $record) {
        Fail "MSI does not contain a ProductName property."
    }

    $productName = $record.StringData(1)
    $view.Close()

    if ($productName -notmatch "SimpleIDE") {
        Fail "MSI does not declare SimpleIDE as ProductName. Received: $productName"
    }

    # Check MSI Summary Information platform.
    $summary = $database.SummaryInformation(0)
    $template = $summary.Property(7)

    $expectedTemplate = switch ($arch) {
        "x86"   { "Intel" }
        "x64"   { "x64" }
        "arm64" { "Arm64" }
    }

    if ($template -notmatch [regex]::Escape($expectedTemplate)) {
        Fail "MSI platform mismatch. Expected $expectedTemplate; received '$template'."
    }

    Ok "MSI product name: $productName"
    Ok "MSI platform: $template"
}
catch {
    Fail "Windows Installer cannot validate $msi ($($_.Exception.Message))"
}

# --------------------------------------------------
# Cleanup
# --------------------------------------------------

Remove-Item -Recurse -Force $extractDir

# --------------------------------------------------
# Final result
# --------------------------------------------------

Ok $archive
Ok $msi

Write-Host ""
Write-Host "All packages for $os-$arch are valid." -ForegroundColor Green

exit 0
