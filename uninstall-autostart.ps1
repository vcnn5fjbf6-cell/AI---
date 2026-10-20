$ErrorActionPreference = "Stop"
$taskName = "AI-HR-Platform-Supervisor"
$powershellPath = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principalCheck = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principalCheck.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
  Write-Host "需要管理员权限来移除开机自启，正在请求提升..."
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
  Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
  Write-Host "已移除开机自启任务：$taskName"
} else {
  Write-Host "未找到开机自启任务：$taskName"
}

Write-Host "常驻守护已停止。已运行的 MySQL、后端和前端不会被自动关闭；如需关闭请运行 stop-system.bat"