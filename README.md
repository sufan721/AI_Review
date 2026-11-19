# 备忘录

> 一个**自托管、轻量极简**的备忘录/笔记应用。用 Java + Vue 重新实现。

## 设计理念

1. **自托管、隐私优先**：数据完全掌控在自己手里，无追踪、无广告、无订阅费。
2. **开源、永久免费**：代码开源透明，不绑定任何厂商。
3. **轻量、极简**：纯文本 + Markdown，拒绝复杂富文本的心智负担，界面干净。
4. **快速部署、低资源占用**：一条命令即可跑起来，资源占用小。
5. **可定制、可分享**：支持自定义主题，笔记可对外分享、协作。
6. **API 优先**：提供完整 REST 接口，方便第三方工具与自动化脚本集成。

## 技术栈

| 层 | 技术 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3 + TypeScript + Vite | 纯前端，通过 REST 调用后端 |
| 前端状态/路由 | Pinia + Vue Router | 状态管理与路由 |
| UI | 原生 CSS + CSS 变量 | 项目定位轻量极简，暂不引入组件库 |
| 后端 | Java 17 + Spring Boot 3 | REST 接口服务 |
| 数据访问 | MyBatis-Plus | ORM 数据访问 |
| 数据库 | MySQL 8 | 生产数据库 |
| 缓存/会话 | Redis 7 | 验证码、限流、刷新令牌与黑名单等带 TTL 的状态 |

## 架构

```
┌─────────────────────────────────────────────┐
│                 前端 (Vue 3)                 │  纯前端,调 REST API
└──────────────────────┬──────────────────────┘
                       │  REST (JSON)
┌──────────────────────▼──────────────────────┐
│               Controller 层                  │  接收请求、参数校验、返回统一结果
├─────────────────────────────────────────────┤
│               Service 层                     │  业务逻辑
├─────────────────────────────────────────────┤
│               Mapper 层                      │  数据访问(MyBatis-Plus)
└──────────────────────┬──────────────────────┘
                       │
                  ┌────▼────┐
                  │  MySQL  │
                  └─────────┘
```

### 核心原则

1. **单向依赖**：Controller → Service → Mapper，上层调下层，下层不反向依赖。
2. **分层职责清晰**：Controller 只管收发，Service 管业务，Mapper 管 SQL，互不越界。
3. **前后端分离**：前端只通过 API 通信，不碰数据库。

## 目录结构

```
backend/                          # Java 后端 (Spring Boot)
├── pom.xml
└── src/main/
    ├── java/com/aireview/
    │   ├── AiReviewApplication.java   # 启动类
    │   ├── controller/                # 接口层：收发请求、参数校验
    │   ├── service/                   # 业务层：核心业务逻辑
    │   │   └── impl/                  # 业务实现
    │   ├── mapper/                    # 数据层：MyBatis-Plus Mapper
    │   ├── entity/                    # 数据库实体
    │   ├── dto/                       # 出入参对象 (DTO/VO)
    │   ├── config/                    # 配置 (MyBatis、CORS、拦截器)
    │   ├── security/                  # 鉴权 (JWT 会话、RBAC、验证码、状态存储)
    │   ├── index/                     # 资料切片、嵌入与 Milvus 索引
    │   └── common/                    # 公共 (统一返回 Result、全局异常)
    └── resources/
        ├── application.yml            # 配置文件
        └── mapper/                    # MyBatis XML 映射
frontend/                         # 前端 (Vue 3 + TS)
├── package.json
├── vite.config.ts
└── src/
    ├── api/                           # 接口封装 (fetch + 统一 Result 解析)
    ├── views/                         # 页面
    ├── components/                    # 通用组件
    ├── stores/                        # Pinia 状态
    ├── router/                        # Vue Router 路由
    ├── types/                         # TS 类型定义
    └── utils/                         # 工具函数
docs/                             # 文档、SQL 脚本
```

## 快速开始

前置要求：Docker Desktop（Docker Compose v2）、JDK 17、Node.js 20+。

### IDEA 启动（推荐）

用 IDEA 打开仓库根目录后，右上角运行配置下拉框里可直接选择以下配置（已内置在 `.idea/runConfigurations/`）：

| 运行配置 | 作用 |
| --- | --- |
| **Start All (前后端一键启动)** | 依次启动基础设施 + 后端 + 前端（推荐） |
| Infra (Docker 基础设施) | 只启动 MySQL / Redis / etcd / MinIO / Milvus |
| Backend (Maven 通用) | 只启动后端（8080），调用 `mvnw.cmd spring-boot:run` |
| Backend (Spring Boot) | 只启动后端（8080），Spring Boot 配置类型，仅 IntelliJ IDEA Ultimate 可用 |
| Frontend (Vite) | 只启动前端（5173），需要 IDE 的 Node.js 支持 |

> Infra 与 Backend (Maven 通用) 都是 Shell 运行配置，直接调用外部命令，不依赖 IDE 的 Docker 连接或 Java 支持：Infra 执行 `docker compose ... up -d --wait`，无需在 `Settings → Build, Execution, Deployment → Docker` 里预先添加 Docker 连接；Backend (Maven 通用) 在 PyCharm / GoLand 里也能用。
> 只有 IntelliJ IDEA Ultimate 支持 Spring Boot 配置类型（带 Spring 面板、热重启、断点调试）；其他 IDE 用 Backend (Maven 通用)，能起服务但没有这些面板。GoLand 没有 Node.js 支持，前端需在终端执行 `npm run dev`。

**首次导入后的准备（只需一次）：**

1. **把后端的 Maven 工程导入进来**：右键 `backend/pom.xml` → `Add as Maven Project`（根目录的 `.idea` 里没有 Maven 工程记录，不导入的话模块上没有任何依赖库，Spring 相关的类全部标红、启动必然失败）。首次同步会解析依赖，之后都走 `~/.m2` 缓存。
2. 确认已安装 JDK 17，并在 `File → Project Structure → Project` 里把 SDK 设为 JDK 17（`backend/pom.xml` 里 `<java.version>` 是 17）。
3. 确认 Docker Desktop 已启动（一键启动会自动拉起 MySQL/Redis/Milvus 等容器，并利用 MySQL 的 initdb 机制自动建表，无需手动执行建表脚本）。
4. 前端依赖：若 `frontend/node_modules` 不存在，先在 `frontend` 目录执行 `npm install`。

之后日常开发直接点 **Start All (前后端一键启动)** 即可。启动完成后访问：

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- SSO 模拟 IdP：http://localhost:8080/api/auth/sso/mock

> 说明：后端运行配置已内置 DB_URL（`localhost:3307`，对应 Docker 映射端口）、Redis、Milvus 等环境变量，无需手动 export。若只想重启前后端、不重启动基础设施，分别单独运行 Backend 与 Frontend (Vite) 即可。

### 手动启动（宿主机运行前后端）

```bash
# 1. 先准备 deploy/.env（首次可复制 deploy/.env.example），启动基础设施并导入表结构
# docs/sql/schema.sql 是唯一建表来源
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --wait mysql redis etcd minio milvus
docker compose -f deploy/docker-compose.yml --env-file deploy/.env cp docs/sql/schema.sql mysql:/tmp/schema.sql
docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/schema.sql'

# 2. 后端（本机 MySQL 映射在宿主机 3307 端口）
cd backend
# Windows PowerShell: set these variables with $env:DB_URL, $env:DB_USERNAME, $env:DB_PASSWORD, $env:REDIS_HOST, and $env:MILVUS_HOST, then run .\mvnw.cmd spring-boot:run
DB_URL='jdbc:mysql://localhost:3307/memo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai' \
  DB_USERNAME=memo DB_PASSWORD=memo REDIS_HOST=localhost MILVUS_HOST=localhost \
  ./mvnw spring-boot:run

# 3. 前端
cd frontend
npm install
npm run dev
```

### 手动全容器部署

启动基础设施并导入 schema 后，再构建并启动应用容器（首次初始化不能在 schema 导入前启动后端）：

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --wait mysql redis etcd minio milvus
docker compose -f deploy/docker-compose.yml --env-file deploy/.env cp docs/sql/schema.sql mysql:/tmp/schema.sql
docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/schema.sql'
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build backend frontend
```

容器中的后端使用 Compose 服务名连接 MySQL `mysql:3306`、Redis `redis:6379`、Milvus `milvus:19530`，不要改成宿主机映射地址。默认后端 http://localhost:8080 、前端 http://localhost:8088 ，可在 `deploy/.env` 自定义端口。

查看后端日志：

```powershell
docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f backend
```

停止容器但保留数据库等数据卷：

```powershell
docker compose -f deploy/docker-compose.yml --env-file deploy/.env down
```

不要添加 `-v`，除非确实要删除持久化数据。

## 更多文档

- [architecture.md](./architecture.md) — 项目架构设计说明
- [CONTRIBUTING.md](./CONTRIBUTING.md) — 贡献指南（分支/提交规范、代码红线、PR 流程）
- [docs/需求分析.md](./docs/需求分析.md) — 需求模型、功能编号与验收口径
- [docs/执行路线图-从当前状态到可演示MVP.md](./docs/执行路线图-从当前状态到可演示MVP.md) — 当前执行顺序
- [docs/RBAC-禅道式权限设计讨论稿.md](./docs/RBAC-禅道式权限设计讨论稿.md) — RBAC 方案讨论

## 当前进度

已交付：

- 基础框架：统一 API 响应与异常处理、健康检查、MySQL / Redis / Milvus（含 etcd、MinIO）本地编排与 IDEA 一键启动配置。
- 认证：图形验证码、密码登录（失败锁定 + 防枚举）、JWT 会话（jti + 刷新令牌轮换 + 登出黑名单）、
  短信/邮件验证码登录（验证码打印到服务端终端）、找回密码、OIDC 统一身份认证（轻量对接 + 模拟 IdP）、
  RBAC 角色（`ROLE_USER` / `ROLE_ADMIN`）、登录审计日志，以及对应的前端页面与登录态路由守卫。
- 笔记：CRUD、置顶、归档、分页与 Markdown 双栏编辑器（渲染时关闭原生 HTML，避免 XSS）。
- 文件：Markdown 上传（仅 `.md`、限 2MB）、列表、预览、删除与重索引。
- 索引：切片 → 千问百炼嵌入 → Milvus 写入，含 `PENDING` / `INDEXED` / `FAILED` 状态与失败原因。
- 数据持久化：管理后台「数据持久化」面板已接通真实能力——类型化设置（后端校验/规范化）、
  存储与索引概览、手动/自动整库快照（MyBatis 逻辑导出为 zip + SHA-256 校验）、保留期清理、
  校验/下载/删除、恢复前安全快照与事务恢复，恢复后自动重建向量索引。详见
  [数据持久化完成方案](./docs/数据持久化-完成方案.md)。

未完成（按执行路线图推进）：知识库与成员权限、知识库问答与引用、标签与搜索、分享与主题。

## 测试

```bash
# 后端：集成测试默认用 Testcontainers 起 MySQL 8.4 容器（需要 Docker Desktop）。
# Docker Engine 29+ 需固定客户端 API 版本，已通过 src/test/resources/docker-java.properties 处理。
cd backend
./mvnw test        # Windows PowerShell 用 .\mvnw.cmd test

# 若环境无法启动容器，可指向一个已建表的专用测试库：
TEST_DB_URL='jdbc:mysql://localhost:3307/memo?useUnicode=true&characterEncoding=UTF-8' \
  TEST_DB_USERNAME=memo TEST_DB_PASSWORD=memo \
  ./mvnw test

# 前端
npm --prefix frontend run test -- --run
npm --prefix frontend run build
```

> 集成测试会清空所用库中的业务表，`TEST_DB_URL` 必须指向专用的测试库，不要指向开发库。

