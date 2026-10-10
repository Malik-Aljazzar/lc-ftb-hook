# Dev-Umgebungen (Claim-Mod x Waehrungs-Mod) - muss zu devEnvs in build.gradle passen.
# Wird von start-server.ps1 / start-client.ps1 per Dot-Sourcing geladen.
$DevEnvs = @{
    "ftb-lc"   = @{
        Name = "FTB Chunks + Lightman's Currency"; Port = 25565; Rcon = 25575
        ServerTask = "runServer"; ServerDir = "run"
        OpsFile = "dev/ops.json"; Properties = "dev/server.properties"
        ClientTasks = @("runClient", "runClient2", "runClient3")
    }
    "opac-num" = @{
        Name = "Open Parties and Claims + Numismatics"; Port = 25566; Rcon = 25576
        ServerTask = "runServerOpac"; ServerDir = "run-opac-server"
        OpsFile = "dev/ops-opac.json"; Properties = "dev/server-opac.properties"
        ClientTasks = @("runClientOpac1", "runClientOpac2", "runClientOpac3")
    }
    "ftb-num"  = @{
        Name = "FTB Chunks + Numismatics"; Port = 25567; Rcon = 25577
        ServerTask = "runServerFtbNum"; ServerDir = "run-ftb-num-server"
        OpsFile = "dev/ops.json"; Properties = "dev/server-ftb-num.properties"
        ClientTasks = @("runClientFtbNum1", "runClientFtbNum2")
    }
    "opac-lc"  = @{
        Name = "Open Parties and Claims + Lightman's Currency"; Port = 25568; Rcon = 25578
        ServerTask = "runServerOpacLc"; ServerDir = "run-opac-lc-server"
        OpsFile = "dev/ops-opac.json"; Properties = "dev/server-opac-lc.properties"
        ClientTasks = @("runClientOpacLc1", "runClientOpacLc2")
    }
}

function Use-Jdk21 {
    $jdk21 = Get-ChildItem "C:\Program Files\Microsoft\jdk-21*" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $jdk21) {
        Write-Host "JDK 21 nicht gefunden. Installiere mit: winget install Microsoft.OpenJDK.21" -ForegroundColor Red
        exit 1
    }
    $env:JAVA_HOME = $jdk21
    $env:PATH = "$jdk21\bin;$env:PATH"
    Write-Host "JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
}
