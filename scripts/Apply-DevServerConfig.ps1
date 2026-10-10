# Wendet Dev-Server-Einstellungen an: Creative + OP fuer die Dev-Client-Usernamen (siehe build.gradle)
param(
    [string]$RunDir = (Join-Path (Split-Path $PSScriptRoot -Parent) "run"),
    # dev/ops-opac.json (OpacOwner/OpacOfficer) for the OPAC dev server - see
    # scripts/run-server-opac.ps1. Relative to this script's own directory,
    # same convention as the hardcoded dev/ftbchunks-world.snbt etc. below.
    [string]$OpsFile = "dev/ops.json",
    # dev/server-opac.properties for the OPAC dev server: same content as
    # dev/server.properties but on distinct server-port/rcon.port (25566/25576
    # vs. the main server's 25565/25575) so both dev servers can run at the
    # same time without a port clash.
    [string]$PropertiesTemplate = "dev/server.properties"
)

$ErrorActionPreference = "Stop"

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        # Not Mandatory: PowerShell's mandatory-parameter check rejects an
        # empty string *per array element*, not just a wholly-empty argument -
        # any blank line (normal in a sectioned .toml/.properties file) would
        # make it throw "cannot bind ... because it is an empty string".
        [AllowEmptyCollection()][string[]]$Lines
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8NoBom)
}

if (-not (Test-Path $RunDir)) {
    New-Item -ItemType Directory -Path $RunDir | Out-Null
}

Copy-Item (Join-Path $PSScriptRoot $OpsFile) (Join-Path $RunDir "ops.json") -Force

$ftbChunksConfig = Join-Path $PSScriptRoot "dev/ftbchunks-world.snbt"
$serverConfigDir = Join-Path $RunDir "world/serverconfig"
if (Test-Path $ftbChunksConfig) {
    New-Item -ItemType Directory -Path $serverConfigDir -Force | Out-Null
    Copy-Item $ftbChunksConfig (Join-Path $serverConfigDir "ftbchunks-world.snbt") -Force
}

$templatePath = Join-Path $PSScriptRoot $PropertiesTemplate
$propsPath = Join-Path $RunDir "server.properties"

# server.properties can balloon if a previous script run appended/corrupted it
# (seen up to ~1.5 GB). Always reset from the checked-in dev template.
$needsReset = $true
if (Test-Path $propsPath) {
    $size = (Get-Item $propsPath).Length
    if ($size -lt 4096) {
        $needsReset = $false
    } else {
        Write-Host "server.properties ist $size Bytes (vermutlich korrupt) -> wird aus Dev-Template neu geschrieben." -ForegroundColor Yellow
    }
}

if ($needsReset) {
    if (-not (Test-Path $templatePath)) {
        Write-Host "Dev-Template fehlt: $templatePath" -ForegroundColor Red
        exit 1
    }
    Copy-Item $templatePath $propsPath -Force
} else {
    # Patch only the keys we care about on an otherwise healthy file.
    $lines = Get-Content $propsPath
    $overrides = @{
        "gamemode" = "creative"
        "force-gamemode" = "true"
        "online-mode" = "false"
        "enforce-secure-profile" = "false"
        "spawn-protection" = "0"
        # RCON lets scripts/rcon-stop.py send a real "stop" command instead of
        # force-killing the java process (via TaskStop) - a forceful kill skips
        # Minecraft's own save-on-shutdown routine entirely, silently losing
        # anything since the last autosave tick (the exact "money/force-load
        # I just did was suddenly gone" symptom). Dev-only, localhost-only,
        # so a simple fixed password is fine here.
        "enable-rcon" = "true"
        "rcon.password" = "devonly"
    }
    $seen = @{}

    $updated = foreach ($line in $lines) {
        if ($line -match '^(?<key>[^#=]+)=') {
            $key = $Matches.key.Trim()
            if ($overrides.ContainsKey($key)) {
                $seen[$key] = $true
                "$key=$($overrides[$key])"
                continue
            }
        }
        $line
    }

    foreach ($entry in $overrides.GetEnumerator()) {
        if (-not $seen.ContainsKey($entry.Key)) {
            $updated += "$($entry.Key)=$($entry.Value)"
        }
    }

    $tempPath = "$propsPath.tmp"
    Write-Utf8NoBomFile -Path $tempPath -Lines $updated
    Move-Item -Path $tempPath -Destination $propsPath -Force
}

$modConfigTemplate = Join-Path $PSScriptRoot "dev/lc_ftb_hook-server.toml"
$modConfigTargets = @(
    (Join-Path $RunDir "config/lc_ftb_hook-server.toml"),
    (Join-Path $RunDir "world/serverconfig/lc_ftb_hook-server.toml")
)

foreach ($configPath in $modConfigTargets) {
    $configDir = Split-Path $configPath -Parent
    if (-not (Test-Path $configDir)) {
        New-Item -ItemType Directory -Path $configDir -Force | Out-Null
    }

    if (-not (Test-Path $configPath)) {
        if (Test-Path $modConfigTemplate) {
            Copy-Item $modConfigTemplate $configPath -Force
        } else {
            Write-Utf8NoBomFile -Path $configPath -Lines @(
                "[general]",
                "`tupkeepPeriodMinutes = 1"
            )
        }
        continue
    }

    $devPatches = [ordered]@{
        "upkeepPeriodMinutes" = "1"
        "warCostMultiplier" = "1.2"
    }
    $sectionForKey = @{
        "upkeepPeriodMinutes" = "general"
        "warCostMultiplier" = "war"
    }

    $lines = Get-Content $configPath
    $updated = $false
    $seen = @{}
    $patchedList = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        $emitted = $false
        foreach ($entry in $devPatches.GetEnumerator()) {
            $key = $entry.Key
            if ($line -match "^\s*$([regex]::Escape($key))\s*=") {
                $seen[$key] = $true
                if ($line -notmatch "=\s*$([regex]::Escape($entry.Value))\s*$") {
                    $updated = $true
                    $patchedList.Add("`t$key = $($entry.Value)")
                } else {
                    $patchedList.Add($line)
                }
                $emitted = $true
                break
            }
        }
        if (-not $emitted) {
            $patchedList.Add($line)
        }
    }
    $patched = $patchedList.ToArray()

    foreach ($entry in $devPatches.GetEnumerator()) {
        if ($seen.ContainsKey($entry.Key)) {
            continue
        }

        $updated = $true
        $section = $sectionForKey[$entry.Key]
        $inserted = $false
        $inSection = $false
        $newLines = New-Object System.Collections.Generic.List[string]
        foreach ($line in $patched) {
            if (-not $inserted -and $line -match "^\[$section\]") {
                $inSection = $true
                $newLines.Add($line)
                continue
            }
            if (-not $inserted -and $inSection -and $line -match '^\[') {
                $newLines.Add("`t$($entry.Key) = $($entry.Value)")
                $inserted = $true
            }
            $newLines.Add($line)
            if ($inSection -and -not $inserted -and $line -match '^\[') {
                $inSection = $false
            }
        }

        if (-not $inserted) {
            if ($inSection) {
                $newLines.Add("`t$($entry.Key) = $($entry.Value)")
            } else {
                $newLines.Add("[$section]")
                $newLines.Add("`t$($entry.Key) = $($entry.Value)")
            }
        }

        $patched = $newLines.ToArray()
    }

    if ($updated -and $patched.Count -gt 0) {
        $tempPath = "$configPath.tmp"
        Write-Utf8NoBomFile -Path $tempPath -Lines $patched
        Move-Item -Path $tempPath -Destination $configPath -Force
    }
}

Write-Host "Dev-Server: gamemode=creative, force-gamemode=true, online-mode=false, OP laut $OpsFile, ftbchunks-world.snbt, upkeepPeriodMinutes=1" -ForegroundColor Cyan
