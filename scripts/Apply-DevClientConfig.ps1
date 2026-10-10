# Fenster-Vollbild mit Rand: grosses Fenster, kein exklusives Vollbild (F11).
param(
    [string[]]$RunDirs = @(
        (Join-Path (Split-Path $PSScriptRoot -Parent) "clients/ftb-lc-1"),
        (Join-Path (Split-Path $PSScriptRoot -Parent) "clients/ftb-lc-2"),
        (Join-Path (Split-Path $PSScriptRoot -Parent) "clients/ftb-lc-3")
    )
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Windows.Forms
$area = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$width = [int]$area.Width
$height = [int]$area.Height

function Update-OptionsFile {
    param(
        [string]$OptionsPath,
        [int]$Width,
        [int]$Height
    )

    $dir = Split-Path $OptionsPath -Parent
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    $overrides = [ordered]@{
        "fullscreen" = "false"
        "overrideWidth" = "$Width"
        "overrideHeight" = "$Height"
    }

    if (-not (Test-Path $OptionsPath)) {
        $lines = @("version:3955")
    } else {
        $lines = Get-Content $OptionsPath
    }

    $seen = @{}
    $updated = foreach ($line in $lines) {
        $matched = $false
        foreach ($entry in $overrides.GetEnumerator()) {
            if ($line -match "^$([regex]::Escape($entry.Key)):") {
                $seen[$entry.Key] = $true
                "$($entry.Key):$($entry.Value)"
                $matched = $true
                break
            }
        }
        if (-not $matched) {
            $line
        }
    }

    foreach ($entry in $overrides.GetEnumerator()) {
        if (-not $seen.ContainsKey($entry.Key)) {
            $updated += "$($entry.Key):$($entry.Value)"
        }
    }

    $tempPath = "$OptionsPath.tmp"
    [System.IO.File]::WriteAllLines($tempPath, $updated, [System.Text.UTF8Encoding]::new($false))
    Move-Item -Path $tempPath -Destination $OptionsPath -Force
}

foreach ($runDir in $RunDirs) {
    $optionsPath = Join-Path $runDir "options.txt"
    Update-OptionsFile -OptionsPath $optionsPath -Width $width -Height $height
}

$windowPropsDir = Join-Path $PSScriptRoot "dev"
$windowPropsPath = Join-Path $windowPropsDir "client-window.properties"
if (-not (Test-Path $windowPropsDir)) {
    New-Item -ItemType Directory -Path $windowPropsDir -Force | Out-Null
}
[System.IO.File]::WriteAllLines($windowPropsPath, @(
    "width=$width"
    "height=$height"
), [System.Text.UTF8Encoding]::new($false))

Write-Host "Dev-Client: Fenster ${width}x${height} (Vollbild mit Rand, kein exklusives Vollbild)" -ForegroundColor Cyan
