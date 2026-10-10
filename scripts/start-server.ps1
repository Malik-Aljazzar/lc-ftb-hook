# Dev-Server einer Umgebung starten, z. B.: .\scripts\start-server.ps1 -Env opac-lc
param(
    [Parameter(Mandatory = $true)][Alias("Env")][ValidateSet("ftb-lc", "opac-num", "ftb-num", "opac-lc")][string]$Environment
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "dev-envs.ps1")
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$cfg = $DevEnvs[$Environment]

Use-Jdk21
& (Join-Path $PSScriptRoot "Apply-DevServerConfig.ps1") -RunDir (Join-Path $root $cfg.ServerDir) -OpsFile $cfg.OpsFile -PropertiesTemplate $cfg.Properties
Write-Host ""
Write-Host "Starte Dev-Server $Environment ($($cfg.Name)) - Port $($cfg.Port), RCON $($cfg.Rcon)" -ForegroundColor Green
Write-Host "Stoppen: python3 scripts/dev/rcon.py --port $($cfg.Rcon) stop" -ForegroundColor DarkGray
Write-Host "Hinweis: Gradle bleibt bei ~88% EXECUTING solange der Server laeuft - das ist normal." -ForegroundColor DarkGray
& .\gradlew.bat $cfg.ServerTask
