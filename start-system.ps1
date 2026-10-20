$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
$taskName = "AI-HR-Platform-Supervisor"
$supervisorPath = Join-Path $root "service-supervisor.ps1"
$powershellPath = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

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

function Start-LocalSupervisor {
  if (-not (Test-Path -LiteralPath $supervisorPath)) {
    Write-Host "守护脚本不存在：$supervisorPath"
    return
  }
  $arguments = @("-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", "`"$supervisorPath`"")
  Start-Process -FilePath $powershellPath -ArgumentList $arguments -WindowStyle Hidden
}

Write-Host "=========================================="
Write-Host " 数据中心运维平台 - 常驻服务启动"
Write-Host "=========================================="

$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($task) {
  if ($task.State -eq "Running") {
    Write-Host "常驻守护任务已在运行：$taskName"
  } else {
    Write-Host "正在启动常驻守护任务：$taskName"
    try {
      Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
    } catch {
      Write-Host "计划任务启动失败，改用当前登录会话启动守护进程"
      Start-LocalSupervisor
    }
  }
} else {
  Write-Host "未安装开机自启任务，当前使用登录会话守护进程"
  Write-Host "如需开机自动运行，请以管理员身份运行 install-autostart.bat"
  Start-LocalSupervisor
}

Write-Host "正在等待 MySQL、后端、前端就绪..."
$deadline = (Get-Date).AddSeconds(150)
while ((Get-Date) -lt $deadline) {
  if ((Test-PortOpen 3306) -and (Test-PortOpen 8081) -and (Test-PortOpen 5173)) { break }
  Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "MySQL 3306：$(if (Test-PortOpen 3306) { '运行中' } else { '未就绪' })"
Write-Host "后端 8081：$(if (Test-PortOpen 8081) { '运行中' } else { '未就绪' })"
Write-Host "前端 5173：$(if (Test-PortOpen 5173) { '运行中' } else { '未就绪' })"
Write-Host "访问地址：http://localhost:5173"
Write-Host "登录账号：admin2026；密码请通过安全渠道获取，首次登录后立即修改。"
Write-Host "日志目录：$(Join-Path $root '.runtime\logs')"
Write-Host "守护进程会自动恢复停止的服务，无需反复重启。"
Write-Host "停止服务请运行 stop-system.bat"
Write-Host "=========================================="