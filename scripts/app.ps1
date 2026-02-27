$ErrorActionPreference = "Stop"

param(
    [Parameter(Position = 0)]
    [string]$Command
)

$scriptHome = Split-Path -Parent $MyInvocation.MyCommand.Path
$appHome = Split-Path -Parent $scriptHome

$javaHome = ""
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME_17)) {
    $javaHome = $env:JAVA_HOME_17
} elseif (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaHome = $env:JAVA_HOME
}

if ([string]::IsNullOrWhiteSpace($javaHome) -or -not (Test-Path -LiteralPath $javaHome -PathType Container)) {
    Write-Error "cannot found java_home: $javaHome"
    exit 1
}

$javaCmd = Join-Path $javaHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaCmd -PathType Leaf)) {
    Write-Error "cannot found executable java_cmd: $javaCmd"
    exit 1
}

$env:JAVA_HOME = $javaHome

$appRuntimeHome = if (-not [string]::IsNullOrWhiteSpace($env:APP_RUNTIME_HOME)) { $env:APP_RUNTIME_HOME } else { $appHome }
$springProfilesActive = if (-not [string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) { $env:SPRING_PROFILES_ACTIVE } else { "prod" }

$agentJar = Get-ChildItem -Path $scriptHome -File -Filter "convention4j-agent-*.jar" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

function Show-Env {
    Write-Output "script_home: $scriptHome"
    Write-Output "app_home: $appHome"
    Write-Output "app_runtime_home: $appRuntimeHome"
    Write-Output "java_home: $javaHome"
    Write-Output "java_cmd: $javaCmd"
    Write-Output "agent_jar: $($agentJar.FullName)"
    Write-Output "spring_profiles_active: $springProfilesActive"
}

function Build-App {
    Push-Location $appHome
    try {
        & mvn clean install -Dmaven.test.skip=true
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

function Get-AppPid {
    $pidFile = Join-Path $appRuntimeHome "app.pid"
    if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) {
        return ""
    }

    $value = (Get-Content -LiteralPath $pidFile -TotalCount 1 -ErrorAction SilentlyContinue).Trim()
    return $value
}

function Stop-App {
    $pidFile = Join-Path $appRuntimeHome "app.pid"
    $appPid = Get-AppPid
    if (-not [string]::IsNullOrWhiteSpace($appPid)) {
        Stop-Process -Id ([int]$appPid) -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

function Start-App {
    New-Item -ItemType Directory -Force -Path (Join-Path $appRuntimeHome "logs") | Out-Null

    $jarDir = Join-Path $appHome "web\target"
    $webJar = Get-ChildItem -Path $jarDir -File -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $webJar) {
        Write-Error "Error: web jar not found under $jarDir. Did you run scripts/app build?"
        exit 1
    }

    $javaOpts = @(
        "-server",
        "-Xms64M",
        "-Xmx256M",
        "-Dsun.net.inetaddr.ttl=30",
        "-Dsun.net.inetaddr.negative.ttl=5"
    )

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_OPTS)) {
        $javaOpts += ($env:JAVA_OPTS -split "\s+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    }

    if ($env:DEBUG -eq "true") {
        $javaOpts += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:10000"
    }

    if ($null -ne $agentJar) {
        $javaOpts += "-javaagent:$($agentJar.FullName)"
    }

    $appArgs = @(
        "-jar",
        $webJar.FullName,
        "--spring.profiles.active=$springProfilesActive",
        "--logging.file.path=$(Join-Path $appRuntimeHome 'logs')"
    )

    if ($springProfilesActive -eq "dev") {
        & $javaCmd @javaOpts @appArgs
        exit $LASTEXITCODE
    }

    $outLog = Join-Path (Join-Path $appRuntimeHome "logs") "console.out.log"
    $errLog = Join-Path (Join-Path $appRuntimeHome "logs") "console.err.log"
    $process = Start-Process -FilePath $javaCmd -ArgumentList ($javaOpts + $appArgs) -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

    Set-Content -LiteralPath (Join-Path $appRuntimeHome "app.pid") -Value $process.Id
    Write-Output "started pid=$($process.Id)"
}

function Show-Help {
    Write-Output "Usage: app {env|build|start|stop|pid}"
}

switch ($Command) {
    "env" {
        Show-Env
    }
    "build" {
        Build-App
    }
    "start" {
        Start-App
    }
    "stop" {
        Stop-App
    }
    "pid" {
        Write-Output (Get-AppPid)
    }
    default {
        Show-Help
        exit 1
    }
}
