$ErrorActionPreference = "Stop"

param(
    [Parameter(Position = 0)]
    [string]$McpName,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$PassthroughArgs
)

function Show-Usage {
    Write-Output "Usage: mmh-cli {all|open-browser}"
    Write-Output "Example: mmh-cli all"
    Write-Output "Example: mmh-cli open-browser"
}

function Resolve-FullPathOrNull {
    param([string]$InputPath)

    try {
        return [System.IO.Path]::GetFullPath($InputPath)
    } catch {
        return $null
    }
}

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

if ([string]::IsNullOrWhiteSpace($McpName)) {
    Show-Usage
    exit 1
}

$extraArgs = @()
if ($McpName -eq "open-browser") {
    $McpName = "all"
    $extraArgs = @(
        "--open-browser",
        "--spring.ai.mcp.server.enabled=false",
        "--spring.ai.mcp.server.annotation-scanner.enabled=false"
    )
} elseif ($McpName -eq "util") {
    $McpName = "all"
}

$targetDir = Join-Path $appHome ("cli\cli-{0}\target" -f $McpName)
if (-not (Test-Path -LiteralPath $targetDir -PathType Container)) {
    Write-Error "Error: jar target dir for mcp '$McpName' not found. Did you run 'scripts/app build'?"
    exit 1
}

$jarFile = Get-ChildItem -Path $targetDir -File -Filter ("my-mcp-hub-cli-{0}-*.jar" -f $McpName) |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $jarFile) {
    Write-Error "Error: jar for mcp '$McpName' not found. Did you run 'scripts/app build'?"
    exit 1
}

$defaultLogRoot = Join-Path $HOME ".my-mcp-hub\logs"
$logRoot = if (-not [string]::IsNullOrWhiteSpace($env:MMH_LOG_BASE_DIR)) { $env:MMH_LOG_BASE_DIR } else { $defaultLogRoot }

$agentId = ""
if (-not [string]::IsNullOrWhiteSpace($env:MMH_AGENT_ID)) {
    $agentId = $env:MMH_AGENT_ID
} elseif (-not [string]::IsNullOrWhiteSpace($env:AGENT_ID)) {
    $agentId = $env:AGENT_ID
} else {
    $agentId = "$PID"
}

$agentIdSanitized = [System.Text.RegularExpressions.Regex]::Replace($agentId, "[^a-zA-Z0-9._-]", "_")
if ([string]::IsNullOrWhiteSpace($agentIdSanitized)) {
    $agentIdSanitized = "$PID"
}

$logHome = Join-Path $logRoot ("agent-{0}" -f $agentIdSanitized)
New-Item -ItemType Directory -Force -Path $logHome | Out-Null

$logRetentionDays = if (-not [string]::IsNullOrWhiteSpace($env:MMH_LOG_RETENTION_DAYS)) { $env:MMH_LOG_RETENTION_DAYS } else { "7" }
if ($logRetentionDays -match "^[1-9][0-9]*$") {
    $retentionDays = [int]$logRetentionDays
    $resolvedLogRoot = Resolve-FullPathOrNull -InputPath $logRoot
    $resolvedDefaultLogRoot = Resolve-FullPathOrNull -InputPath $defaultLogRoot
    $allowCleanupOutsideDefault = ($env:MMH_LOG_CLEAN_ALLOW_OUTSIDE_DEFAULT -eq "true")
    $rootPath = if ($null -ne $resolvedLogRoot) { [System.IO.Path]::GetPathRoot($resolvedLogRoot) } else { $null }

    if ([string]::IsNullOrWhiteSpace($resolvedLogRoot) -or $resolvedLogRoot -eq $rootPath) {
        Write-Warning "skip log cleanup: unsafe log root '$logRoot'"
    } elseif (
        -not $allowCleanupOutsideDefault -and
        -not [string]::IsNullOrWhiteSpace($resolvedDefaultLogRoot) -and
        $resolvedLogRoot -ne $resolvedDefaultLogRoot -and
        -not $resolvedLogRoot.StartsWith($resolvedDefaultLogRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
    ) {
        Write-Warning "skip log cleanup: '$resolvedLogRoot' is outside default '$resolvedDefaultLogRoot' (set MMH_LOG_CLEAN_ALLOW_OUTSIDE_DEFAULT=true to allow)"
    } else {
        if (Test-Path -LiteralPath $logRoot -PathType Container) {
            Get-ChildItem -Path $logRoot -Recurse -File -Filter "*.gz" -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$retentionDays) } |
                Remove-Item -Force -ErrorAction SilentlyContinue

            $currentLogHomeFull = Resolve-FullPathOrNull -InputPath $logHome
            Get-ChildItem -Path $logRoot -Directory -Filter "agent-*" -ErrorAction SilentlyContinue |
                ForEach-Object {
                    $agentDir = $_
                    $agentDirFull = Resolve-FullPathOrNull -InputPath $agentDir.FullName
                    if ($agentDirFull -eq $currentLogHomeFull) {
                        return
                    }

                    $agentName = $agentDir.Name
                    $agentPid = $agentName -replace "^agent-", ""

                    if ($agentPid -notmatch "^[0-9]+$") {
                        return
                    }

                    if (Get-Process -Id ([int]$agentPid) -ErrorAction SilentlyContinue) {
                        return
                    }

                    $hasRecentFiles = Get-ChildItem -Path $agentDir.FullName -Recurse -File -ErrorAction SilentlyContinue |
                        Where-Object { $_.LastWriteTime -ge (Get-Date).AddDays(-$retentionDays) } |
                        Select-Object -First 1

                    if ($null -eq $hasRecentFiles) {
                        Remove-Item -LiteralPath $agentDir.FullName -Recurse -Force -ErrorAction SilentlyContinue
                    }
                }
        }
    }
} else {
    Write-Warning "skip log cleanup: invalid MMH_LOG_RETENTION_DAYS='$logRetentionDays' (must be positive integer)"
}

$javaOpts = @(
    "-server",
    "-Xss256k",
    "-Xms32M",
    "-Xmx512M",
    "-Dspring.main.banner-mode=off"
)

if (-not [string]::IsNullOrWhiteSpace($env:JAVA_OPTS)) {
    $javaOpts += ($env:JAVA_OPTS -split "\s+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

$appArgs = @(
    "--spring.main.web-application-type=none",
    "--logging.file.path=$logHome",
    "--logging.level.root=WARN",
    "--logging.level.fun.fengwk.mmh=INFO"
)

$allArgs = @()
$allArgs += $javaOpts
$allArgs += "-jar"
$allArgs += $jarFile.FullName
$allArgs += $appArgs
$allArgs += $extraArgs
$allArgs += $PassthroughArgs

& $javaCmd @allArgs
exit $LASTEXITCODE
