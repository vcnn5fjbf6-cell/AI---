$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$runtimeDir = Join-Path $root ".runtime"
$logDir = Join-Path $runtimeDir "logs"
$stateDir = Join-Path $runtimeDir "state"
New-Item -ItemType Directory -Force -Path $logDir, $stateDir | Out-Null

# 单实例锁：任务和手工启动同时存在时，只保留一个守护进程
$lockPath = Join-Path $runtimeDir "supervisor.lock"
$lockStream = $null
try {
  $lockStream = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
  exit 0
}

$logPath = Join-Path $logDir "supervisor.log"
try {
  if ((Test-Path -LiteralPath $logPath) -and ((Get-Item -LiteralPath $logPath).Length -gt 5MB)) {
    Move-Item -LiteralPath $logPath -Destination "$logPath.old" -Force
  }
} catch { }

function Write-Log {
  param([string]$Message)
  try {
    $line = "{0} [{1}] {2}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $PID, $Message
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
  } catch { }
}

function Import-EnvFile {
  param([string]$Path)
  if (-not (Test-Path -LiteralPath $Path)) { return }
  Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) { return }
    $kv = $line.Split("=", 2)
    $name = $kv[0].Trim()
    $value = $kv[1].Trim()
    if ($value.Length -ge 2) {
      $quotedDouble = $value.StartsWith('"') -and $value.EndsWith('"')
      $quotedSingle = $value.StartsWith("'") -and $value.EndsWith("'")
      if ($quotedDouble -or $quotedSingle) { $value = $value.Substring(1, $value.Length - 2) }
    }
    if ($name) { [Environment]::SetEnvironmentVariable($name, $value, "Process") }
  }
}

function Test-TcpPort {
  param([int]$Port)
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $ar = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
    if (-not $ar.AsyncWaitHandle.WaitOne(700)) { return $false }
    $client.EndConnect($ar)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Test-HttpOk {
  param([string]$Url)
  try {
    $resp = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
    return ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500)
  } catch {
    return $false
  }
}

function Get-PortOwnerProcess {
  param([int]$Port)
  try {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop | Select-Object -First 1
    if ($conn) { return Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue }
  } catch { }

  $line = netstat -ano | Select-String ":$Port\s" | Select-String "LISTENING" | Select-Object -First 1
  if ($line) {
    $ownerId = ($line.ToString() -split "\s+")[-1]
    if ($ownerId -and $ownerId -ne "0") { return Get-Process -Id ([int]$ownerId) -ErrorAction SilentlyContinue }
  }
  return $null
}

function Test-ProcessOwned {
  param([int]$ProcessId, [string]$Marker)
  try {
    $info = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction Stop
    if ($info -and $info.CommandLine) {
      return $info.CommandLine.IndexOf($Marker, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
    }
  } catch { }
  return $false
}

$dev = "C:\Users\admin\.devstack"
$mysqlBase = Join-Path $dev "mysql-8.0.42-winx64"
$mysqlBin = Join-Path $mysqlBase "bin\mysqld.exe"
$mysqlAdmin = Join-Path $mysqlBase "bin\mysqladmin.exe"
$dataDir = Join-Path $dev "mysql-data"
$javaPath = "D:\PyCharm 2024.1\jbr\bin\java.exe"
$nodePath = "C:\Program Files\nodejs\node.exe"
$backendDir = Join-Path $root "backend-springboot"
$frontendDir = Join-Path $root "frontend-vue"
$jarPath = Join-Path $backendDir "target\enterprise-procurement-backend-0.1.0.jar"
$vitePath = Join-Path $frontendDir "node_modules\vite\bin\vite.js"

Import-EnvFile (Join-Path $root "feishu.env")
$env:VITE_API_TARGET = "http://localhost:8081"

$script:tracked = @{ mysql = $null; backend = $null; frontend = $null }
$script:lastStart = @{
  mysql = [DateTime]::MinValue
  backend = [DateTime]::MinValue
  frontend = [DateTime]::MinValue
}
$script:healthFailures = @{ backend = 0; frontend = 0 }

function Reset-RuntimeLog {
  param([string]$Path)
  try {
    if (Test-Path -LiteralPath $Path) { Clear-Content -LiteralPath $Path -Force }
  } catch { }
}

function Start-MySql {
  if (-not (Test-Path -LiteralPath $mysqlBin)) {
    Write-Log "ERROR: MySQL executable not found: $mysqlBin"
    return
  }
  $out = Join-Path $logDir "mysql.out.log"
  $err = Join-Path $logDir "mysql.err.log"
  Reset-RuntimeLog $out
  Reset-RuntimeLog $err
  $proc = Start-Process -FilePath $mysqlBin -ArgumentList @("--basedir=$mysqlBase", "--datadir=$dataDir", "--port=3306", "--console") -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
  $script:tracked.mysql = $proc
  $script:lastStart.mysql = Get-Date
  Write-Log "MySQL start requested (PID $($proc.Id))"
}

function Start-Backend {
  if (-not (Test-Path -LiteralPath $jarPath)) {
    Write-Log "ERROR: backend jar not found: $jarPath"
    return
  }
  $out = Join-Path $logDir "backend.out.log"
  $err = Join-Path $logDir "backend.err.log"
  Reset-RuntimeLog $out
  Reset-RuntimeLog $err
  $proc = Start-Process -FilePath $javaPath -ArgumentList @("-jar", "`"$jarPath`"", "--server.port=8081") -WorkingDirectory $backendDir -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
  $script:tracked.backend = $proc
  $script:lastStart.backend = Get-Date
  Write-Log "Backend start requested (PID $($proc.Id))"
}

function Start-Frontend {
  if (-not (Test-Path -LiteralPath $vitePath)) {
    Write-Log "ERROR: Vite entry not found: $vitePath"
    return
  }
  $out = Join-Path $logDir "frontend.out.log"
  $err = Join-Path $logDir "frontend.err.log"
  Reset-RuntimeLog $out
  Reset-RuntimeLog $err
  $proc = Start-Process -FilePath $nodePath -ArgumentList @("`"$vitePath`"", "--host", "127.0.0.1", "--port", "5173", "--strictPort") -WorkingDirectory $frontendDir -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
  $script:tracked.frontend = $proc
  $script:lastStart.frontend = Get-Date
  Write-Log "Frontend start requested (PID $($proc.Id))"
}

function Stop-Tracked {
  param([string]$Name)
  $proc = $script:tracked[$Name]
  if ($proc) {
    try {
      $proc.Refresh()
      if (-not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
      }
    } catch { }
    $script:tracked[$Name] = $null
  }
}

function Stop-ServiceProcess {
  param([string]$Name, [int]$Port, [string]$Marker)
  Stop-Tracked $Name
  if (-not (Test-TcpPort $Port)) { return }

  $owner = Get-PortOwnerProcess $Port
  if (-not $owner) { return }
  if (Test-ProcessOwned -ProcessId $owner.Id -Marker $Marker) {
    Write-Log "Stopping $Name process on port $Port (PID $($owner.Id))"
    Stop-Process -Id $owner.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 500
  } else {
    Write-Log "WARN: port $Port is occupied by an unrelated process (PID $($owner.Id)); not stopping it"
  }
}

function Test-CanStart {
  param([string]$Name, [int]$CooldownSeconds)
  return ((Get-Date) - $script:lastStart[$Name]).TotalSeconds -ge $CooldownSeconds
}

function Write-Status {
  $status = [ordered]@{
    updatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    supervisorPid = $PID
    mysql = (Test-TcpPort 3306)
    backendPort = (Test-TcpPort 8081)
    backendHealth = (Test-HttpOk "http://127.0.0.1:8081/health")
    frontend = (Test-HttpOk "http://127.0.0.1:5173/")
  }
  try {
    $status | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stateDir "status.json") -Encoding UTF8
  } catch { }
}

Write-Log "Supervisor started"

while ($true) {
  try {
    if (-not (Test-TcpPort 3306)) {
      if (Test-CanStart -Name "mysql" -CooldownSeconds 20) { Start-MySql }
    }

    if (-not (Test-TcpPort 8081)) {
      $script:healthFailures.backend = 0
      if (Test-CanStart -Name "backend" -CooldownSeconds 30) { Start-Backend }
    } elseif (Test-HttpOk "http://127.0.0.1:8081/health") {
      $script:healthFailures.backend = 0
    } else {
      $script:healthFailures.backend++
      Write-Log "Backend port is open but health check failed ($($script:healthFailures.backend)/6)"
      if ($script:healthFailures.backend -ge 6 -and (Test-CanStart -Name "backend" -CooldownSeconds 120)) {
        Write-Log "Backend is unhealthy; restarting"
        Stop-ServiceProcess -Name "backend" -Port 8081 -Marker "enterprise-procurement-backend"
        Start-Backend
        $script:healthFailures.backend = 0
      }
    }

    if (-not (Test-TcpPort 5173)) {
      $script:healthFailures.frontend = 0
      if (Test-CanStart -Name "frontend" -CooldownSeconds 30) { Start-Frontend }
    } elseif (Test-HttpOk "http://127.0.0.1:5173/") {
      $script:healthFailures.frontend = 0
    } else {
      $script:healthFailures.frontend++
      Write-Log "Frontend port is open but HTTP check failed ($($script:healthFailures.frontend)/6)"
      if ($script:healthFailures.frontend -ge 6 -and (Test-CanStart -Name "frontend" -CooldownSeconds 120)) {
        Write-Log "Frontend is unhealthy; restarting"
        Stop-ServiceProcess -Name "frontend" -Port 5173 -Marker "vite"
        Start-Frontend
        $script:healthFailures.frontend = 0
      }
    }

    Write-Status
  } catch {
    Write-Log "ERROR: $($_.Exception.Message)"
  }

  Start-Sleep -Seconds 10
}