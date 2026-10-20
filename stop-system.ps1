$ErrorActionPreference = "SilentlyContinue"
$root = $PSScriptRoot
$taskName = "AI-HR-Platform-Supervisor"
$powershellPath = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principalCheck = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principalCheck.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
  Write-Host "停止常驻服务需要管理员权限，正在请求提升..."
  $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
  try {
    Start-Process -FilePath $powershellPath -Verb RunAs -ArgumentList $arguments
  } catch {
    Write-Host "无法请求管理员权限：$($_.Exception.Message)"
  }
  exit
}

$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($task) {
  Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
  Write-Host "已停止常驻守护任务：$taskName"
}

# 停止可能由 start-system.bat 启动的当前会话守护进程
Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf("service-supervisor.ps1", [System.StringComparison]::OrdinalIgnoreCase) -ge 0 } |
  ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "已停止守护进程 $($_.ProcessId)"
  }

function Test-PortOpen {
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

function Get-PortOwner {
  param([int]$Port)
  $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $conn) { return $null }
  return Get-CimInstance Win32_Process -Filter "ProcessId=$($conn.OwningProcess)" -ErrorAction SilentlyContinue
}

function Stop-OwnedProcess {
  param([int]$Port, [string]$Marker, [string]$Label)
  if (-not (Test-PortOpen $Port)) { return }
  $proc = Get-PortOwner $Port
  if (-not $proc) { return }
  if ($proc.CommandLine -and $proc.CommandLine.IndexOf($Marker, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "已停止端口 $Port 的$Label进程 $($proc.ProcessId)"
  } else {
    Write-Host "端口 $Port 被其他程序占用，已跳过：PID $($proc.ProcessId)"
  }
}

# 优先正常关闭 MySQL，再处理后端和前端
$mysqlAdmin = "C:\Users\admin\.devstack\mysql-8.0.42-winx64\bin\mysqladmin.exe"
if ((Test-Path -LiteralPath $mysqlAdmin) -and (Test-PortOpen 3306)) {
  & $mysqlAdmin --host=127.0.0.1 --port=3306 --user=root --password=root shutdown 2>$null | Out-Null
  Start-Sleep -Seconds 2
}
Stop-OwnedProcess -Port 3306 -Marker "mysqld" -Label "MySQL"
Stop-OwnedProcess -Port 8081 -Marker "enterprise-procurement-backend" -Label "后端"
Stop-OwnedProcess -Port 5173 -Marker "vite" -Label "前端"

Write-Host "本机服务已全部停止"