# 备忘录 · 具体实现方案

> 本文档是 [README.md](./README.md) 的补充，给出「备忘录」应用从数据库、后端、前端到部署的**具体实现方案**。目标：拿到本文档即可开工，无需再反复确认设计。

---

## 目录

1. [功能范围](#1-功能范围)
2. [总体架构](#2-总体架构)
3. [数据库设计](#3-数据库设计)
4. [后端实现方案](#4-后端实现方案)
5. [前端实现方案](#5-前端实现方案)
6. [核心功能实现细节](#6-核心功能实现细节)
7. [分阶段实施计划](#7-分阶段实施计划)
8. [部署方案](#8-部署方案)
9. [约定与规范](#9-约定与规范)

---

## 1. 功能范围

### 1.1 用户与认证

| 功能 | 说明 | 优先级 |
| --- | --- | --- |
| 注册 / 登录 | 用户名 + 密码，登录后签发 JWT | P0 |
| 身份鉴权 | 除公开分享外，所有接口需携带 Token | P0 |

### 1.2 笔记

| 功能 | 说明 | 优先级 |
| --- | --- | --- |
| 笔记 CRUD | 标题 + Markdown 正文 | P0 |
| 置顶 / 归档 | 笔记列表排序与归档收纳 | P0 |
| 标签 | 一笔记多标签，按标签过滤 | P1 |
| 全文搜索 | 按标题 / 正文关键词搜索 | P1 |

### 1.3 分享与协作

| 功能 | 说明 | 优先级 |
| --- | --- | --- |
| 分享链接 | 生成短 Token 分享笔记 | P1 |
| 分享权限 | 只读 / 可编辑，可设置有效期 | P2 |

### 1.4 主题定制

| 功能 | 说明 | 优先级 |
| --- | --- | --- |
| 内置主题 | 亮色 / 暗色等预设 | P1 |
| 自定义主题 | 颜色、字体等可配置，存服务端 | P2 |

---

## 2. 总体架构

![总体架构图](./docs/b6dd5665-679d-43a2-a1a1-1396e4498df7.png)

**关键决策**

| 决策点 | 选择 | 理由 |
| --- | --- | --- |
| 认证方案 | JWT（`jjwt` 库）+ 拦截器 | 无状态、易扩展、贴合前后端分离 |
| ORM | MyBatis-Plus | 减少模板代码，复杂查询可写 XML |
| 统一返回 | `Result<T>` | 前端处理统一、全局异常兜底 |
| Markdown 渲染 | 前端 `markdown-it` + `highlight.js` | 极简、可控，无需后端参与 |
| 编辑体验 | 双栏实时预览（`CodeMirror 6` 或 `textarea`） | 保持轻量 |

---

## 3. 数据库设计

数据库名：`memo`，字符集 `utf8mb4`，排序规则 `utf8mb4_general_ci`。

### 3.1 表清单

| 表名 | 说明 |
| --- | --- |
| `user` | 用户 |
| `note` | 笔记 |
| `tag` | 标签 |
| `note_tag` | 笔记-标签关联 |
| `share` | 分享 |
| `user_theme` | 用户自定义主题 |

### 3.2 建表 SQL

```sql
CREATE DATABASE IF NOT EXISTS memo DEFAULT CHARSET utf8mb4;

-- 用户
CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户';

-- 笔记
CREATE TABLE `note` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL COMMENT '所属用户',
  `title`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '标题',
  `content`         LONGTEXT     COMMENT 'Markdown 正文',
  `is_pinned`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶 0/1',
  `is_archived`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否归档 0/1',
  `content_version` INT          NOT NULL DEFAULT 1 COMMENT '内容版本，正文变更时递增',
  `index_status`    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '索引状态 PENDING/INDEXED/FAILED',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_archived_pinned` (`user_id`, `is_archived`, `is_pinned`, `updated_at`)
) ENGINE=InnoDB COMMENT='笔记';

-- 标签
CREATE TABLE `tag` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL,
  `name`       VARCHAR(64) NOT NULL COMMENT '标签名',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE=InnoDB COMMENT='标签';

-- 笔记-标签关联
CREATE TABLE `note_tag` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT,
  `note_id` BIGINT NOT NULL,
  `tag_id`  BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_tag` (`note_id`, `tag_id`),
  KEY `idx_tag` (`tag_id`)
) ENGINE=InnoDB COMMENT='笔记标签关联';

-- 分享
CREATE TABLE `share` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `note_id`    BIGINT       NOT NULL,
  `token`      VARCHAR(32)  NOT NULL COMMENT '分享短链 token',
  `mode`       VARCHAR(16)  NOT NULL DEFAULT 'readonly' COMMENT 'readonly / editable',
  `expires_at` DATETIME     DEFAULT NULL COMMENT '过期时间，NULL 为永久',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_note` (`note_id`)
) ENGINE=InnoDB COMMENT='分享';

-- 用户自定义主题
CREATE TABLE `user_theme` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `name`       VARCHAR(64)  NOT NULL,
  `config`     JSON         COMMENT '主题配置(颜色/字体等)',
  `is_default` TINYINT      NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='自定义主题';
```

> 建表脚本统一放入 `docs/sql/schema.sql`，随仓库交付。

### 3.3 关系说明

- `note.user_id → user.id`：一个用户多篇笔记。
- `note.content_version` / `note.index_status`：正文每次有效变更时 `content_version` 递增并把 `index_status` 置回 `PENDING`，索引侧据此判断已有切片是否过期（见 6.4）。
- `note_tag`：笔记与标签的**多对多**关联。
- `share.note_id → note.id`：一篇笔记可生成多个分享链接。

---

## 4. 后端实现方案

### 4.1 依赖（`pom.xml` 关键项）

```xml
<properties>
  <java.version>17</java.version>
  <spring-boot.version>3.2.x</spring-boot.version>
  <mybatis-plus.version>3.5.x</mybatis-plus.version>
</properties>

<dependencies>
  <dependency>org.springframework.boot:spring-boot-starter-web</dependency>
  <dependency>org.springframework.boot:spring-boot-starter-validation</dependency>
  <dependency>com.baomidou:mybatis-plus-spring-boot3-starter</dependency>
  <dependency>com.mysql:mysql-connector-j</dependency>
  <dependency>io.jsonwebtoken:jjwt-api</dependency>
  <dependency>io.jsonwebtoken:jjwt-impl</dependency>
  <dependency>io.jsonwebtoken:jjwt-jackson</dependency>
  <dependency>org.springframework.security:spring-security-crypto</dependency> <!-- BCrypt -->
  <dependency>org.projectlombok:lombok</dependency>
</dependencies>
```

### 4.2 代码结构

```
backend/src/main/java/com/aireview/
├── AiReviewApplication.java
├── controller/          # AuthController, NoteController, TagController, ShareController, ThemeController
├── service/
│   ├── AuthService, NoteService, TagService, ShareService, ThemeService
│   └── impl/
├── mapper/              # UserMapper, NoteMapper, TagMapper, NoteTagMapper, ShareMapper, UserThemeMapper
├── entity/              # User, Note, Tag, NoteTag, Share, UserTheme
├── dto/                 # 出入参对象 (见 4.4)
├── config/              # WebConfig(CORS), MybatisPlusConfig, JwtInterceptor, SecurityConfig(密码加密)
├── common/
│   ├── Result.java      # 统一返回
│   ├── ResultCode.java  # 状态码枚举
│   ├── PageResult.java  # 分页返回
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
└── util/                # JwtUtil, UserContext(ThreadLocal 存当前用户)
```

### 4.3 统一返回与异常

```java
public class Result<T> {
    private int code;      // 0 成功，非 0 失败
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) { ... }
    public static <T> Result<T> ok() { ... }
    public static <T> Result<T> fail(int code, String msg) { ... }
}

public class PageResult<T> {
    private List<T> records;
    private long total;
    private long page;
    private long size;
}
```

`GlobalExceptionHandler` 统一捕获 `BusinessException`、`MethodArgumentNotValidException`、`Exception`，分别返回业务错误、参数错误、500。

### 4.4 DTO 设计

| DTO | 字段 | 用途 |
| --- | --- | --- |
| `RegisterRequest` | username, password, nickname | 注册 |
| `LoginRequest` | username, password | 登录 |
| `LoginResponse` | token, userId, username, nickname | 登录返回 |
| `NoteCreateRequest` | title, content, tagIds[] | 新建笔记 |
| `NoteUpdateRequest` | title, content, tagIds[] | 更新笔记 |
| `NoteQueryRequest` | keyword, tagId, isPinned, isArchived, page, size | 列表查询 |
| `NoteVO` | id, title, content, isPinned, isArchived, tags[], createdAt, updatedAt | 笔记返回 |
| `TagRequest` | name | 标签增改 |
| `ShareCreateRequest` | mode, expiresAt | 创建分享 |
| `ShareVO` | id, token, mode, expiresAt, url | 分享返回 |
| `ThemeRequest` | name, config, isDefault | 主题增改 |

> 原则：**Controller 不直接暴露 `entity`**，出入参一律用 DTO/VO 隔离数据库结构。

### 4.5 API 设计

统一前缀 `/api`，除登录/注册/公开分享外均需 `Authorization: Bearer <token>`。

#### 认证 `AuthController`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/auth/me` | 获取当前用户信息 |

#### 笔记 `NoteController`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/notes` | 分页列表（支持 keyword / tagId / isPinned / isArchived） |
| POST | `/api/notes` | 新建 |
| GET | `/api/notes/{id}` | 详情 |
| PUT | `/api/notes/{id}` | 更新（含标签） |
| DELETE | `/api/notes/{id}` | 删除（级联删 note_tag / share） |
| PATCH | `/api/notes/{id}/pin` | 置顶/取消置顶 |
| PATCH | `/api/notes/{id}/archive` | 归档/取消归档 |

#### 标签 `TagController`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/tags` | 当前用户标签列表 |
| POST | `/api/tags` | 新建标签 |
| PUT | `/api/tags/{id}` | 重命名 |
| DELETE | `/api/tags/{id}` | 删除（级联删 note_tag） |

#### 分享 `ShareController`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/notes/{id}/share` | 生成分享链接 |
| GET | `/api/notes/{id}/shares` | 某笔记的分享列表 |
| DELETE | `/api/shares/{id}` | 撤销分享 |
| GET | `/api/share/{token}` | **公开**：按 token 读取笔记（含只读/可编辑判定） |
| PUT | `/api/share/{token}` | **公开**：可编辑分享下更新笔记 |

#### 主题 `ThemeController`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/themes` | 我的主题列表 |
| POST | `/api/themes` | 新建主题 |
| PUT | `/api/themes/{id}` | 更新主题 |
| DELETE | `/api/themes/{id}` | 删除主题 |

### 4.6 鉴权实现

1. `JwtUtil`：`generateToken(userId)` / `parseToken(token)`，密钥放 `application.yml`，过期时间默认 7 天。
2. `JwtInterceptor` 实现 `HandlerInterceptor`：
   - 从请求头取 `Authorization`，解析出 `userId` 存入 `UserContext`（`ThreadLocal`）。
   - 未登录 / 过期 → 返回 `401`。
3. `WebConfig` 注册拦截器，放行白名单：`/api/auth/**`、`/api/share/**`（公开分享）、静态资源。
4. **越权校验**：Service 层所有按 `id` 操作均以 `user_id = 当前用户` 为条件查询，杜绝水平越权。

### 4.7 配置文件 `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/memo?useUnicode=true&characterEncoding=UTF-8
    username: root
    password: ${DB_PASSWORD:root}
  jackson:
    time-zone: GMT+8

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

jwt:
  secret: ${JWT_SECRET:change-me}
  expire-hours: 168
```

---

## 5. 前端实现方案

### 5.1 依赖（关键项）

```jsonc
{
  "dependencies": {
    "vue": "^3.4",
    "vue-router": "^4",
    "pinia": "^2",
    "axios": "^1",
    "element-plus": "^2",
    "markdown-it": "^14",
    "highlight.js": "^11",
    "codemirror": "^6"          // 编辑器（可选，先用 textarea 也可）
  },
  "devDependencies": {
    "typescript": "^5",
    "vite": "^5",
    "@vitejs/plugin-vue": "^5"
  }
}
```

### 5.2 路由设计

| 路径 | 组件 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| `/login` | `views/Login.vue` | 登录 | 否 |
| `/register` | `views/Register.vue` | 注册 | 否 |
| `/` | 重定向到 `/notes` | — | — |
| `/notes` | `views/NoteList.vue` | 笔记列表 | 是 |
| `/notes/:id` | `views/NoteEdit.vue` | 编辑器 | 是 |
| `/settings` | `views/Settings.vue` | 设置（主题） | 是 |
| `/shared/:token` | `views/Shared.vue` | 公开分享页 | 否 |

### 5.3 页面与组件

| 组件 | 路径 | 职责 |
| --- | --- | --- |
| `AppLayout` | `components/layout/` | 侧边栏 + 主内容布局 |
| `Sidebar` | `components/layout/` | 笔记列表、标签过滤、新建按钮 |
| `NoteItem` | `components/note/` | 单条笔记卡片（标题/时间/标签/置顶标） |
| `MarkdownEditor` | `components/markdown/` | 编辑区（CodeMirror） |
| `MarkdownRenderer` | `components/markdown/` | 渲染区（markdown-it + highlight.js） |
| `TagSelect` | `components/tag/` | 标签选择器（可多选、可新建） |
| `ThemeEditor` | `components/theme/` | 自定义主题配置表单 |

### 5.4 状态管理（Pinia）

| Store | 状态 | 说明 |
| --- | --- | --- |
| `auth` | token, user | 登录态，token 持久化到 localStorage |
| `notes` | list, current, query | 笔记列表、当前笔记、查询条件 |
| `tags` | list | 标签列表 |
| `theme` | current, list | 当前主题、主题列表 |

### 5.5 API 封装（`src/api/`）

- `request.ts`：axios 实例，请求拦截器注入 `Authorization`，响应拦截器统一处理 `Result`（code !== 0 时弹错误、401 跳登录）。
- 各模块封装：`auth.ts`、`note.ts`、`tag.ts`、`share.ts`、`theme.ts`，导出类型化方法。

### 5.6 类型定义（`src/types/`）

与后端 DTO/VO 一一对应：`User.ts`、`Note.ts`、`Tag.ts`、`Share.ts`、`Theme.ts`、`Result.ts`、`PageResult.ts`。

---

## 6. 核心功能实现细节

### 6.1 Markdown 编辑与渲染

- 编辑器采用 **双栏布局**：左侧 CodeMirror 编辑，右侧 `markdown-it` 实时渲染。
- `highlight.js` 代码高亮；`markdown-it` 开启 `linkify`、`typographer`。
- **XSS 防护**：渲染前对原始 HTML 做白名单过滤（`html: false`），或引入 `DOMPurify` 净化。

### 6.2 全文搜索

- 初版用 MySQL `LIKE '%keyword%'`（标题 + 正文），数据量小时足够。
- 优化方向（后续）：标题加 `FULLTEXT` 索引，或引入 Elasticsearch。

### 6.3 标签维护

- 新建/更新笔记时，前端提交 `tagIds[]`，后端在事务内先删后插 `note_tag` 关联。
- 删除标签时级联删除关联，不影响笔记本身。

### 6.4 分享

- 创建分享生成 32 位随机 `token`（`UUID` 去横线），短链为 `/shared/{token}`。
- 公开接口 `GET /api/share/{token}` 校验：token 有效、未过期；`mode` 决定是否允许 `PUT` 更新。
- 分享页走独立前端路由 `/shared/:token`，**不带**登录态 UI。

### 6.5 主题定制

- 主题 `config` 为 JSON，字段约定：`{ "bg": "#fff", "fg": "#222", "accent": "#409eff", "font": "system-ui" }`。
- 前端将主题映射为 CSS 变量（`:root` 上 `--bg`、`--fg`、`--accent`），全局样式引用变量。
- 切换主题即时生效，`is_default` 的主题在登录后自动应用。

---

## 7. 分阶段实施计划

| 阶段 | 内容 | 产出 | 验收标准 |
| --- | --- | --- | --- |
| **P0 脚手架** | 后端 Spring Boot 骨架、前端 Vite 骨架、连通性验证 | 两个可运行的空工程 | 后端 `/api/health` 返回 200；前端可访问 |
| **P0 认证** | 建库表 `user`，注册/登录/鉴权拦截器 | 登录页 + JWT | 注册登录可用，未登录访问受保护接口返回 401 |
| **P0 笔记 CRUD** | `note` 表 + 笔记接口 + 列表/编辑器页 | 笔记增删改查 + Markdown 渲染 | 可新建、编辑、删除笔记并实时预览 |
| **P1 标签/搜索** | `tag`/`note_tag` 表 + 标签接口 + 搜索过滤 | 标签栏 + 搜索框 | 可按标签过滤、关键词搜索 |
| **P1 分享** | `share` 表 + 分享接口 + 分享页 | 分享链接生成与访问 | 生成的链接可匿名访问笔记 |
| **P2 主题** | `user_theme` 表 + 主题接口 + 主题设置页 | 主题切换 | 可切换/自定义主题并持久化 |
| **P2 部署** | Dockerfile + docker-compose + nginx | 一键部署 | 一条命令拉起全栈 |

---

## 8. 部署方案

```
deploy/
├── docker-compose.yml     # mysql + backend + frontend(nginx)
├── backend/Dockerfile     # 多阶段构建：maven build → jre17 运行
├── frontend/Dockerfile    # node build → nginx 托管静态资源 + 反代 /api
└── nginx.conf             # 前端静态资源 + /api 反代到 backend:8080
```

- **后端**：多阶段 Dockerfile，产物为 Spring Boot fat jar。
- **前端**：`vite build` 产物交给 nginx，`/api` 反向代理到后端容器。
- **环境变量**：数据库密码、JWT 密钥通过 `.env` 注入，不写死在仓库。

```bash
cd deploy
docker compose up -d        # 一条命令启动 mysql + backend + frontend
```

---

## 9. 约定与规范

1. **命名**：Java 类名 PascalCase、方法 camelCase；前端组件 PascalCase、TS 文件 camelCase；SQL 表/字段 `snake_case`。
2. **返回码**：`0` 成功；`401` 未登录；`403` 无权限；`404` 不存在；`500` 服务异常；业务错误用 `4xxx` 自定义。
3. **时间**：后端统一 `GMT+8`，返回 `yyyy-MM-dd HH:mm:ss` 字符串。
4. **密码**：一律 `BCrypt` 加密存储，日志/接口绝不回传明文。
5. **事务**：涉及多表写操作（笔记+标签、删笔记级联）加 `@Transactional`。
6. **SQL 脚本**：`docs/sql/schema.sql` 为唯一建表来源，修改表结构时同步更新。
