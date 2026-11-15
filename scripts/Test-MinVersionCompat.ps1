# Builds (and by default runs) the dev server against the *minimum* NeoForge /
# FTB Library / FTB Teams / FTB Chunks versions declared in neoforge.mods.toml
# (neo_version_min / ftb_library_version_min / ftb_teams_version_min /
# ftb_chunks_version_min in gradle.properties), instead of the newer versions
# the project normally builds against.
#
# Gradle command-line -P properties override gradle.properties for a single
# invocation, so this never touches the file - it's a one-off compat check.
#
# Run this whenever you bump neo_version or an FTB *_version and want to
# confirm the mod still actually works on the floor you're promising players
# in versionRange, not just that it compiles against the newer one.
#
# Usage:
#   scripts\Test-MinVersionCompat.ps1            # build + launch dev server on the floor versions
#   scripts\Test-MinVersionCompat.ps1 -BuildOnly  # just compile/build, no server launch

param(
    [switch]$BuildOnly
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

function Get-GradleProperty([string]$name) {
    $line = Get-Content gradle.properties | Where-Object { $_ -match "^\s*$name\s*=" } | Select-Object -First 1
    return ($line -replace "^\s*$name\s*=\s*", '').Trim()
}

$neoMin = Get-GradleProperty 'neo_version_min'
$ftbLibMin = Get-GradleProperty 'ftb_library_version_min'
$ftbTeamsMin = Get-GradleProperty 'ftb_teams_version_min'
$ftbChunksMin = Get-GradleProperty 'ftb_chunks_version_min'

if (-not $neoMin -or -not $ftbLibMin -or -not $ftbTeamsMin -or -not $ftbChunksMin) {
    Write-Host "Could not find neo_version_min / ftb_library_version_min / ftb_teams_version_min / ftb_chunks_version_min in gradle.properties." -ForegroundColor Red
    exit 1
}

$jdk21 = Get-ChildItem "C:\Program Files\Microsoft\jdk-21*" -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $jdk21) {
    Write-Host "JDK 21 nicht gefunden. Installiere mit: winget install Microsoft.OpenJDK.21" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk21
$env:PATH = "$jdk21\bin;$env:PATH"

Write-Host "Testing against the mods.toml FLOOR versions (not the usual build versions):" -ForegroundColor Cyan
Write-Host "  neo_version        -> $neoMin" -ForegroundColor Cyan
Write-Host "  ftb_library_version -> $ftbLibMin" -ForegroundColor Cyan
Write-Host "  ftb_teams_version   -> $ftbTeamsMin" -ForegroundColor Cyan
Write-Host "  ftb_chunks_version  -> $ftbChunksMin" -ForegroundColor Cyan
Write-Host ""

$gradleArgs = @(
    "-Pneo_version=$neoMin",
    "-Pftb_library_version=$ftbLibMin",
    "-Pftb_teams_version=$ftbTeamsMin",
    "-Pftb_chunks_version=$ftbChunksMin"
)

if ($BuildOnly) {
    & .\gradlew.bat build @gradleArgs
} else {
    & (Join-Path $PSScriptRoot "Apply-DevServerConfig.ps1")
    Write-Host ""
    Write-Host "Starte NeoForge Dev-Server auf der Mindestversion..." -ForegroundColor Green
    & .\gradlew.bat runServer @gradleArgs
}
