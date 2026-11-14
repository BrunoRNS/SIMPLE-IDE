# Builds the release packages for Windows with the naming convention:
#
#   simpleide-v<version>-windows-<arch>.zip     static, portable app image
#   simpleide-v<version>-windows-<arch>.msi     installer
#
# Default behavior: build both. Set SIMPLEIDE_PKG_TYPE to app-image or msi to
# build only that package.

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$properties = Join-Path $rootDir "simpleide\src\main\resources\simpleide.properties"
$fatJar = Join-Path $rootDir "simpleide\target\simpleide.jar"
$pkgDir = Join-Path $rootDir "simpleide\target\pkg"
$inputDir = Join-Path $rootDir "simpleide\target\jpackage-input"

if (-not (Test-Path $fatJar)) {
    throw "$fatJar was not found. Run 'make jar' first."
}

$version = (Get-Content $properties | Where-Object { $_ -match '^version=' } | Select-Object -First 1) -replace '^version=', ''
$version = $version.Trim()
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "Unable to read the application version from $properties"
}

$appVersion = ($version -replace '[^0-9.].*$', '').TrimEnd('.')
if ([string]::IsNullOrWhiteSpace($appVersion)) {
    $appVersion = "1.0.0"
}

$os = if ($env:SIMPLEIDE_PKG_OS) { $env:SIMPLEIDE_PKG_OS } else { "windows" }
$arch = if ($env:SIMPLEIDE_PKG_ARCH) {
    $env:SIMPLEIDE_PKG_ARCH
} else {
    switch ($env:PROCESSOR_ARCHITECTURE) {
        "AMD64" { "x64" }
        "ARM64" { "arm64" }
        "x86" { "x86" }
        default { "x64" }
    }
}
$pkgType = if ($env:SIMPLEIDE_PKG_TYPE) { $env:SIMPLEIDE_PKG_TYPE } else { $null }
$artifactBase = "simpleide-v$version-$os-$arch"

$validOs = @("linux", "macos", "windows")
$validArch = @("x86", "x64", "arm64")
if ($validOs -notcontains $os) {
    throw "Unsupported jpackage OS: $os"
}
if ($validArch -notcontains $arch) {
    throw "Unsupported jpackage architecture: $arch"
}

if ($pkgType) {
    if (@("app-image", "msi") -notcontains $pkgType) {
        throw "Unsupported package type: $pkgType"
    }
    $packages = @($pkgType)
} else {
    $packages = @("app-image", "msi")
}

if (Test-Path $pkgDir) {
    Remove-Item -Recurse -Force $pkgDir
}
if (Test-Path $inputDir) {
    Remove-Item -Recurse -Force $inputDir
}
New-Item -ItemType Directory -Path $pkgDir -Force | Out-Null
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
Copy-Item $fatJar (Join-Path $inputDir "simpleide.jar")

$jpackageArgs = @(
    "--name", "SimpleIDE",
    "--app-version", $appVersion,
    "--input", $inputDir,
    "--main-jar", "simpleide.jar",
    "--main-class", "io.simpleide.App",
    "--vendor", "SimpleIDE",
    "--description", "SimpleIDE",
    "--copyright", "BSD-3-Clause",
    "--java-options", "-Dfile.encoding=UTF-8"
)

function Build-AppImage {
    $imageDir = Join-Path $pkgDir "image"
    if (Test-Path $imageDir) {
        Remove-Item -Recurse -Force $imageDir
    }
    New-Item -ItemType Directory -Path $imageDir -Force | Out-Null
    & jpackage @jpackageArgs --type app-image --dest $imageDir
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }

    $appImage = Join-Path $imageDir "SimpleIDE"
    # On Windows the jpackage launcher sits at the root of the app image.
    $launcher = Join-Path $appImage "SimpleIDE.exe"
    if (-not (Test-Path $appImage)) {
        throw "jpackage did not create the expected application image: $appImage"
    }
    if (-not (Test-Path $launcher)) {
        $launcher = Get-ChildItem -Path $appImage -Filter "SimpleIDE.exe" -Recurse | Select-Object -First 1
    }
    if ($null -eq $launcher -or -not (Test-Path $launcher)) {
        throw "The application image has no launcher: $appImage\SimpleIDE.exe"
    }

    $archive = Join-Path $pkgDir "$artifactBase.zip"
    if (Test-Path $archive) {
        Remove-Item -Force $archive
    }
    Compress-Archive -Path $appImage -DestinationPath $archive -Force
    Write-Host "Package: $archive"
}

function Build-Installer([string]$type) {
    $installerDir = Join-Path $pkgDir "installer-$type"
    if (Test-Path $installerDir) {
        Remove-Item -Recurse -Force $installerDir
    }
    New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
    & jpackage @jpackageArgs --type $type --dest $installerDir
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }

    $installer = Get-ChildItem -Path $installerDir -File | Select-Object -First 1
    if ($null -eq $installer) {
        throw "jpackage did not create a $type installer."
    }
    $artifact = Join-Path $pkgDir "$artifactBase$($installer.Extension)"
    Copy-Item $installer.FullName $artifact -Force
    Write-Host "Package: $artifact"
}

foreach ($type in $packages) {
    if ($type -eq "app-image") {
        Build-AppImage
    } else {
        if (-not (Get-Command candle -ErrorAction SilentlyContinue)) {
            Write-Warning "The WiX toolset (candle.exe) is not installed; skipping the $type installer."
            $script:skipped = "$script:skipped $type"
            continue
        }
        Build-Installer $type
    }
}

$produced = Get-ChildItem -Path $pkgDir -File -Filter "simpleide-v*" -ErrorAction SilentlyContinue
if (-not $produced -or @($produced).Count -eq 0) {
    throw "No package could be produced for $os-$arch. Install the missing dependencies and retry. Skipped:$script:skipped"
}
if ($script:skipped) {
    Write-Warning "Some packages were skipped:$script:skipped"
}
