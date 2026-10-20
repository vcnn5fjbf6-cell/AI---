param(
  [switch]$NoWait
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$taskName = "AI-HR-Platform-Supervisor"
$supervisorPath = Join-Path $root "service-supervisor.ps1"
$powershellPath = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principalCheck = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principalCheck.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
  Write-Host "需要管理员权限来配置开机自启，正在请求提升..."
  $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
  try {
    Start-Process -FilePath $powershellPath -Verb RunAs -ArgumentList $arguments
  } catch {
    Write-Host "无法请求管理员权限：$($_.Exception.Message)"
    Write-Host "请右键以管理员身份运行 install-autostart.bat"
  }
  exit
}

if (-not (Test-Path -LiteralPath $supervisorPath)) {
  throw "守护脚本不存在：$supervisorPath"
}

$action = New-ScheduledTaskAction -Execute $powershellPath -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$supervisorPath`"" -WorkingDirectory $root
$trigger = New-ScheduledTaskTrigger -AtStartup
$taskPrincipal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $taskPrincipal -Settings $settings -Description "AI人力资源平台常驻守护：自动拉起 MySQL、后端和前端" -Force | Out-Null
Start-ScheduledTask -TaskName $taskName

Write-Host "已安装开机自启任务：$taskName"
Write-Host "守护进程已启动，正在等待服务就绪..."

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

if (-not $NoWait) {
  $deadline = (Get-Date).AddSeconds(150)
  while ((Get-Date) -lt $deadline) {
    if ((Test-PortOpen 3306) -and (Test-PortOpen 8081) -and (Test-PortOpen 5173)) { break }
    Start-Sleep -Seconds 2
  }
}

$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
$info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "任务状态：$($task.State)"
Write-Host "MySQL 3306：$(if (Test-PortOpen 3306) { '运行中' } else { '未就绪，请查看日志' })"
Write-Host "后端 8081：$(if (Test-PortOpen 8081) { '运行中' } else { '未就绪，请查看日志' })"
Write-Host "前端 5173：$(if (Test-PortOpen 5173) { '运行中' } else { '未就绪，请查看日志' })"
Write-Host "日志目录：$(Join-Path $root '.runtime\logs')"
if ($info) { Write-Host "上次任务结果：$($info.LastTaskResult)" }
Write-Host ""
Write-Host "访问地址：http://localhost:5173"