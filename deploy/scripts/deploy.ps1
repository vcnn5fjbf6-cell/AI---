$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$deployDir = Resolve-Path (Join-Path $scriptDir "..")
$envFile = Join-Path $deployDir ".env"

if (!(Test-Path $envFile)) {
  Write-Error "$envFile 不存在，请先从 .env.example 复制并修改"
}

Set-Location $deployDir
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps
