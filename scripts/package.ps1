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

# Use the jpackage that belongs to the JDK that built the jar. The Windows
# runner image ships several JDKs on PATH, so resolving the bare "jpackage"
# command can pick a x64 JDK for an x86/ARM64 build and produce an installer
# with the wrong MSI platform template.
$jpackage = "jpackage"
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (Test-Path $candidate) {
        $jpackage = $candidate
    }
}
if (-not (Get-Command $jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found (JAVA_HOME=$env:JAVA_HOME)."
}
Write-Host "Using jpackage: $jpackage"

# jpackage derives the WiX target from os.arch, which reports the host
# architecture rather than the JVM data model. A 32-bit JVM on 64-bit Windows
# therefore reports amd64 and jpackage emits 64-bit WiX components for an x86
# build. Forcing os.arch=x86 makes jpackage pass -arch x86 to candle so the
# components are Intel; the Summary Information template is still corrected
# after the build because the JDK template hard-codes Platform="x64".
$jpackageArgs = @()
if ($arch -eq "x86") {
    $jpackageArgs += "-J-Dos.arch=x86"
}
$jpackageArgs += @(
    "--name", "SimpleIDE",
    "--app-version", $appVersion,
    "--input", $inputDir,
    "--main-jar", "simpleide.jar",
    "--main-class", "io.simpleide.App",
    "--vendor", "SimpleIDE",
    "--description", "SimpleIDE",
    "--copyright", "BSD-3-Clause",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--jlink-options", "--strip-debug --no-man-pages --no-header-files"
)

function Build-AppImage {
    $imageDir = Join-Path $pkgDir "image"
    if (Test-Path $imageDir) {
        Remove-Item -Recurse -Force $imageDir
    }
    New-Item -ItemType Directory -Path $imageDir -Force | Out-Null
    & $jpackage @jpackageArgs --type app-image --dest $imageDir
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

# jpackage 17.0.21's Windows WiX template hard-codes Platform="x64" in the
# Package element, so even -J-Dos.arch=x86 leaves the Summary Information
# template at x64. WixPipeline also has no ARM64 target, so every ARM64 build
# gets an x64 template. The component metadata is already correct for the
# requested architecture (-arch x86 for x86, -arch x64 for ARM64), so only the
# Summary Information platform and page count need correction.
function Set-MsiPlatform([string]$path, [string]$platform, [int]$pageCount) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "MSI not found: $path"
    }

    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $null
    $summary = $null

    try {
        $database = $installer.OpenDatabase($path, 1)
        $summary = $database.SummaryInformation(4)

        $template = [string]$summary.Property(7)
        $currentPageCount = [int]$summary.Property(14)

        if ($template -notmatch "^$([regex]::Escape($platform));" -or $currentPageCount -ne $pageCount) {
            $language = "1033"
            if ($template -match ';\s*(.+)$') {
                $language = $Matches[1]
            }

            $summary.Property(7) = "$platform;$language"
            $summary.Property(14) = [int]$pageCount
            $summary.Persist()

            Write-Host "Patched MSI platform to $platform (was '$template', Page Count $currentPageCount)"
        }
    }
    catch {
        throw "Could not set the MSI platform to $platform in $path ($($_.Exception.Message))"
    }
    finally {
        foreach ($comObject in @($summary, $database, $installer)) {
            if ($null -ne $comObject) {
                [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($comObject)
            }
        }
    }
}

function Build-Installer([string]$type) {
    $installerDir = Join-Path $pkgDir "installer-$type"
    if (Test-Path $installerDir) {
        Remove-Item -Recurse -Force $installerDir
    }
    New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
    & $jpackage @jpackageArgs --type $type --dest $installerDir
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }

    $installer = Get-ChildItem -Path $installerDir -File | Select-Object -First 1
    if ($null -eq $installer) {
        throw "jpackage did not create a $type installer."
    }
    $artifact = Join-Path $pkgDir "$artifactBase$($installer.Extension)"
    Copy-Item $installer.FullName $artifact -Force
    if ($type -eq "msi") {
        if ($arch -eq "x86") {
            Set-MsiPlatform $artifact "Intel" 200
        }
        elseif ($arch -eq "arm64") {
            Set-MsiPlatform $artifact "Arm64" 500
        }
    }
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
