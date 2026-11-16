<#
  全容器一键启动脚本（Windows / PowerShell）。

  做什么：
    1. 首次运行从 deploy/.env.example 生成 deploy/.env；
    2. 启动 MySQL、Redis、etcd、MinIO、Milvus，并等待 MySQL / Redis 健康检查通过；
    3. 导入 docs/sql/schema.sql；
    4. 构建并启动 Docker 中的后端与前端。

  用法：在仓库根目录执行 .\start-docker.ps1
#>

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$composeFile = Join-Path $PSScriptRoot 'deploy/docker-compose.yml'
$envFile     = Join-Path $PSScriptRoot 'deploy/.env'
$envExample  = Join-Path $PSScriptRoot 'deploy/.env.example'
$schemaSql   = Join-Path $PSScriptRoot 'docs/sql/schema.sql'

function Invoke-Compose([string[]]$ComposeArgs) {
    & docker compose -f $composeFile --env-file $envFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose 执行失败: $($ComposeArgs -join ' ')"
    }
}

function Get-EnvValue([string]$Name, [string]$DefaultValue) {
    $line = Get-Content -LiteralPath $envFile |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } |
        Select-Object -First 1
    if ($null -eq $line) { return $DefaultValue }

    $value = ($line -split '=', 2)[1].Trim()
    return $value.Trim('"').Trim("'")
}

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw '未找到 docker 命令。请先安装并启动 Docker Desktop。'
    }
    if (-not (Test-Path -LiteralPath $envExample -PathType Leaf)) {
        throw "缺少环境变量模板: $envExample"
    }
    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
        throw "缺少 Docker Compose 配置: $composeFile"
    }
    if (-not (Test-Path -LiteralPath $schemaSql -PathType Leaf)) {
        throw "缺少数据库建表脚本: $schemaSql"
    }

    Write-Host '==> 检查 Docker Engine 与 Compose'
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw '无法连接 Docker Engine。请确认 Docker Desktop 已启动。'
    }
    & docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw '未找到可用的 Docker Compose v2 插件。'
    }

    Write-Host '==> 准备环境变量'
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        Copy-Item -LiteralPath $envExample -Destination $envFile
        Write-Host "    已从 .env.example 生成 $envFile"
    }

    Write-Host '==> 验证 Docker Compose 配置'
    Invoke-Compose @('config', '--quiet')

    Write-Host '==> 启动基础服务并等待 MySQL / Redis 健康检查通过'
    Invoke-Compose @('up', '-d', '--wait', 'mysql', 'redis', 'etcd', 'minio', 'milvus')

    Write-Host '==> 导入数据库表结构'
    Invoke-Compose @('cp', 'docs/sql/schema.sql', 'mysql:/tmp/schema.sql')
    Invoke-Compose @('exec', '-T', 'mysql', 'sh', '-c', 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/schema.sql')

    Write-Host '==> 构建并启动后端与前端容器'
    Invoke-Compose @('up', '-d', '--build', 'backend', 'frontend')

    $backendPort = Get-EnvValue 'BACKEND_PORT' '8080'
    $frontendPort = Get-EnvValue 'FRONTEND_PORT' '8088'
    Write-Host ''
    Write-Host '全容器启动完成。'
    Write-Host "后端: http://localhost:$backendPort"
    Write-Host "前端: http://localhost:$frontendPort"
    Write-Host "SSO 模拟 IdP: http://localhost:$backendPort/api/auth/sso/mock"
    Write-Host '容器内后端通过 mysql:3306、redis:6379、milvus:19530 连接依赖服务。'
    Write-Host '日志: docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f backend'
    Write-Host '停止: docker compose -f deploy/docker-compose.yml --env-file deploy/.env down（保留数据卷）'
} catch {
    Write-Host "启动失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host '可用以下命令查看容器日志：docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs --tail=100'
    exit 1
}
