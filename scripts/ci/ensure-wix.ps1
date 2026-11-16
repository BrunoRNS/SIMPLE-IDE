# Ensures the WiX toolset is available for jpackage.
#
# The Chocolatey package does not always expose candle.exe on the current
# process PATH, especially on the Windows ARM64 runner. Adding the discovered
# bin directory to GITHUB_PATH makes it visible to the following steps, and
# updating PATH makes it visible to commands in this step.

$ErrorActionPreference = "Stop"

function Find-WixBin {
    $command = Get-Command candle -ErrorAction SilentlyContinue
    if ($command) {
        return Split-Path -Parent $command.Source
    }

    $roots = @(${env:ProgramFiles}, ${env:ProgramFiles(x86)}) | Where-Object { $_ -and (Test-Path $_) }

    $relativePaths = @(
        "WiX Toolset v3.14\bin",
        "WiX Toolset v3.11\bin",
        "WiX Toolset v3.10\bin"
    )

    foreach ($root in $roots) {
        foreach ($relativePath in $relativePaths) {
            $candidate = Join-Path $root $relativePath
            if (Test-Path (Join-Path $candidate "candle.exe")) {
                return $candidate
            }
        }
    }

    foreach ($root in $roots) {
        $found = Get-ChildItem `
            -Path $root `
            -Filter "candle.exe" `
            -File `
            -Recurse `
            -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($found) {
            return $found.Directory.FullName
        }
    }

    return $null
}

$wixBin = Find-WixBin

if (-not $wixBin) {
    Write-Host "Installing the WiX toolset..."
    choco install wixtoolset --yes --no-progress
    if ($LASTEXITCODE -ne 0) {
        throw "choco install wixtoolset failed with exit code $LASTEXITCODE."
    }
    $wixBin = Find-WixBin
}

if (-not $wixBin) {
    throw "The WiX toolset (candle.exe) was not found after installation."
}

Write-Host "Using the WiX toolset at $wixBin"

Add-Content -Path $env:GITHUB_PATH -Value $wixBin
$env:PATH = "$wixBin;$env:PATH"
