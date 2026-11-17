<#
  本地开发一键启动脚本（Windows / PowerShell）。

  做什么：
    1. 首次运行从 deploy/.env.example 生成 deploy/.env；
    2. 用 Docker 拉起基础设施：MySQL、Redis、etcd、MinIO、Milvus；
    3. 等待 MySQL 就绪后导入 docs/sql/schema.sql 建表；
    4. 分别在新窗口启动后端（Spring Boot）与前端（Vite）。

  用法：在仓库根目录执行
    .\dev.ps1
  仅刷新基础设施与建表、不重复启动前后端时加 -NoLaunch：
    .\dev.ps1 -NoLaunch
#>
param(
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$composeFile = Join-Path $PSScriptRoot 'deploy/docker-compose.yml'
$envFile     = Join-Path $PSScriptRoot 'deploy/.env'
$envExample  = Join-Path $PSScriptRoot 'deploy/.env.example'
$schemaSql   = Join-Path $PSScriptRoot 'docs/sql/schema.sql'

function Compose([string[]]$CommandArgs) {
    docker compose -f $composeFile --env-file $envFile @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "docker compose 执行失败: $($CommandArgs -join ' ')" }
}

Write-Host '==> 准备环境变量'
if (-not (Test-Path $envFile)) {
    Copy-Item -LiteralPath $envExample -Destination $envFile
    Write-Host "    已从 .env.example 生成 $envFile"
}

Write-Host '==> 启动基础设施（MySQL / Redis / etcd / MinIO / Milvus）'
Compose -CommandArgs @('up', '-d', '--wait', 'mysql', 'redis', 'etcd', 'minio', 'milvus')

Write-Host '==> 导入建表脚本'
Compose -CommandArgs @('cp', 'docs/sql/schema.sql', 'mysql:/tmp/schema.sql')
Compose -CommandArgs @('exec', '-T', 'mysql', 'sh', '-c', 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/schema.sql')

if ($NoLaunch) {
    Write-Host '==> 基础设施已就绪（-NoLaunch，跳过前后端启动）。'
    exit 0
}

Write-Host '==> 启动后端（新窗口）'
# 本机 MySQL 映射在宿主机 3307（deploy/.env 默认），覆盖默认 3306
$env:DB_URL      = 'jdbc:mysql://localhost:3307/memo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = 'memo'
$env:DB_PASSWORD = 'memo'
$env:REDIS_HOST  = 'localhost'
$env:REDIS_PORT  = '6379'
$env:MILVUS_HOST = 'localhost'
$env:MILVUS_PORT = '19530'
Start-Process powershell -WorkingDirectory (Join-Path $PSScriptRoot 'backend') `
    -WindowStyle Hidden -ArgumentList '-NoProfile', '-Command', '.\mvnw.cmd spring-boot:run'

Write-Host '==> 启动前端（新窗口）'
Start-Process powershell -WorkingDirectory (Join-Path $PSScriptRoot 'frontend') `
    -WindowStyle Hidden -ArgumentList '-NoProfile', '-Command', 'npm run dev'

Write-Host ''
Write-Host '完成。后端 http://localhost:8080 ，前端 http://localhost:5173'
Write-Host 'SSO 模拟 IdP：http://localhost:8080/api/auth/sso/mock'
