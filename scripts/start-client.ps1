# Dev-Client einer Umgebung starten, z. B.: .\scripts\start-client.ps1 -Env opac-lc -Number 1
# Game-Verzeichnis: clients/<Env>-<Number>
param(
    [Parameter(Mandatory = $true)][Alias("Env")][ValidateSet("ftb-lc", "opac-num", "ftb-num", "opac-lc")][string]$Environment,
    [int]$Number = 1
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "dev-envs.ps1")
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$cfg = $DevEnvs[$Environment]
if ($Number -lt 1 -or $Number -gt $cfg.ClientTasks.Count) {
    Write-Host "Umgebung $Environment hat Clients 1..$($cfg.ClientTasks.Count)." -ForegroundColor Red
    exit 1
}
$clientDir = Join-Path $root "clients/$Environment-$Number"

Use-Jdk21
Write-Host "Starte Dev-Client $Environment #$Number ($($cfg.Name))" -ForegroundColor Green
Write-Host "Server zuerst starten (start-server.ps1 -Env $Environment), dann: Multiplayer -> Direct Connect -> localhost:$($cfg.Port)" -ForegroundColor Yellow

# Verhindert Auto-Reconnect zum letzten Server aus servers.dat.
$serversDat = Join-Path $clientDir "servers.dat"
if (Test-Path $serversDat) {
    Remove-Item $serversDat -Force
}
& (Join-Path $PSScriptRoot "Apply-DevClientConfig.ps1") -RunDirs @($clientDir)
& .\gradlew.bat $cfg.ClientTasks[$Number - 1]
