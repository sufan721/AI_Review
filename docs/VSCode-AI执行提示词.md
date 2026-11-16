# VS Code AI 执行提示词

> 使用方式：不要一次把所有提示词都发给 AI。按“总控提示词 → 阶段 0 → 阶段 1……”顺序执行。每完成一个阶段，先检查 diff 和测试结果，再继续下一阶段。

## 一、总控提示词

```text
你现在正在修改一个已有项目，不是从零创建项目。请先阅读代码再修改，禁止根据 README 猜测代码状态。

项目路径：D:\AI_Review

项目最终目标：
这是一个自托管的“备忘录 + 个人知识库问答系统”。用户可以注册登录，创建或进入知识库，上传 Markdown 笔记和文件，系统对资料切片、向量化并建立 Milvus 索引，用户在自己有权限的知识库中提问并获得答案和引用资料。系统需要使用 RBAC 控制管理员、知识库拥有者、编辑成员和只读成员的权限。

当前已经存在的功能和代码：
1. Spring Boot 3 + Java 17 后端，Vue 3 + TypeScript + Vite 前端。
2. MySQL、Redis、etcd、MinIO、Milvus 的 Docker Compose 配置。
3. 统一 Result、PageResult、全局异常处理和健康检查。
4. 笔记 CRUD、分页、置顶、归档、Markdown 双栏编辑器和安全 Markdown 渲染。
5. Markdown 文件上传、列表、详情、删除和重新索引。
6. document_chunk 切片表、异步索引流水线、Embedding 客户端和 MilvusVectorStore。
7. 认证代码已经存在或正在工作区完善：图形验证码、密码登录、短信/邮件验证码登录、找回密码、JWT、刷新令牌、登出黑名单、OIDC/SSO、登录审计。
8. RBAC 基础结构已经存在或正在完善：sys_role、sys_permission、sys_user_role、sys_role_permission、ROLE_USER、ROLE_ADMIN、@RequireRole、@RequirePermission、PermissionService、JwtInterceptor、AdminController。

最终需要补齐的核心业务链路：
用户登录 → 创建知识库 → 添加成员 → 上传 Markdown 资料 → 索引完成 → 在有权限的知识库中提问 → 返回答案和引用。

权限模型的第一版：
- ROLE_USER：普通用户，可以使用自己有权限的知识库。
- ROLE_ADMIN：系统管理员，可以管理用户、角色、权限和审计信息。
- OWNER：知识库拥有者，可以管理知识库、成员和资料。
- EDITOR：知识库编辑成员，可以上传、修改、删除资料。
- VIEWER：知识库只读成员，只能查看资料和提问。

必须遵守的规则：
1. 先使用 rg、目录扫描和源码阅读确认现状，再提出改动方案。
2. 不要重写现有项目，不要删除已有功能，不要创建重复的认证、笔记或索引模块。
3. 以现有代码和数据库脚本为准；README 或架构文档与代码不一致时，先指出差异。
4. 遵守 Controller → Service → Mapper 分层。
5. Controller 不直接暴露 entity，统一使用 DTO/VO 和 Result<T>。
6. 所有按资源 ID 的查询都必须检查当前用户或知识库权限，不能信任前端传入的 userId。
7. 前端接口统一放在 src/api，跨页面状态使用 Pinia，禁止在组件中直接写 axios/fetch 业务请求。
8. 不要为了演示把权限检查只写在前端；后端必须返回 401 或 403。
9. 数据库结构的唯一来源是 docs/sql/schema.sql；修改表结构时同步更新 architecture.md。
10. 每次只完成一个明确阶段，修改前先列出计划，修改后列出文件、测试和剩余风险。
11. 不要自动提交 Git，不要删除用户已有的未提交修改。
12. 如果发现当前代码无法编译或测试失败，先修复阻断问题，不要继续堆新功能。

工作流程：
- 第一步：检查 Git 状态、项目目录和相关源码。
- 第二步：总结现状，指出与本阶段目标有关的已有代码。
- 第三步：提出最小修改计划，等待我确认后再编辑；如果我已经明确要求执行本阶段，则直接实施。
- 第四步：修改代码和测试。
- 第五步：运行相关测试和构建。
- 第六步：汇报修改文件、接口、测试结果和没有完成的事项。
```

## 二、阶段 0：稳定当前项目

```text
执行阶段 0：稳定当前项目，不添加新业务功能。

请先检查：
1. git status；
2. backend/src/main/java 下的认证、RBAC、笔记、文档、索引代码；
3. frontend/src 下的登录、路由、笔记和文档页面；
4. docs/sql/schema.sql；
5. README.md 和 architecture.md。

然后依次运行：
- backend\\mvnw.cmd test
- npm --prefix frontend run test -- --run
- npm --prefix frontend run build

要求：
- 只修复阻断编译、测试或启动的问题；
- 不新增知识库和问答功能；
- 不删除已有未提交修改；
- 找出文档与代码的明显不一致，并列出建议；
- 如果认证相关改动已经能通过测试，只做必要整理，不要重写认证模块。

完成后汇报：构建结果、测试结果、阻断问题、建议提交边界。不要自动 commit。
```

## 三、阶段 1：完成可验证的全局 RBAC

```text
执行阶段 1：在现有认证和 RBAC 代码上完成最小可验证闭环。

目标：
- 普通注册用户默认拥有 ROLE_USER；
- 管理员拥有 ROLE_ADMIN；
- AdminController 的管理接口只有 ROLE_ADMIN 可以访问；
- 普通用户访问管理员接口返回 403；
- 管理员访问成功返回 200。

请先确认现有代码：
- sys_role、sys_permission、sys_user_role、sys_role_permission；
- PermissionService；
- RequireRole、RequirePermission；
- JwtInterceptor；
- AdminController；
- UserMapper 和 schema.sql 中的初始化数据。

只在缺失处补齐：
1. 角色和权限初始化；
2. 普通用户默认角色分配；
3. 管理员接口保护；
4. 管理员查看用户列表接口；
5. 后端集成测试：普通用户 403、管理员 200、未登录 401。

不要现在实现知识库角色 OWNER、EDITOR、VIEWER，也不要创建动态角色管理页面。

完成后运行相关 Maven 测试，并汇报接口路径、权限判断位置、测试结果。
```

## 四、阶段 2：增加知识库和成员权限

```text
执行阶段 2：在现有 note、document 和索引代码之上增加知识库边界。

目标：让 RBAC 进入真实业务，不破坏现有笔记、Markdown 文件和索引功能。

建议新增的最小表：
1. knowledge_base：id、owner_id、name、description、created_at、updated_at；
2. knowledge_base_member：knowledge_base_id、user_id、member_role、created_at；
3. knowledge_base_resource：knowledge_base_id、resource_type、resource_id。

成员角色只有：OWNER、EDITOR、VIEWER。

权限规则：
- OWNER：管理知识库、成员和资料；
- EDITOR：上传、修改、删除知识库资料；
- VIEWER：查看资料和提问；
- 不属于知识库的用户不能访问其中的资料；
- ROLE_ADMIN 可以执行系统管理，但不要默认绕过所有业务数据权限，绕过规则必须明确写在 Service 中。

实施要求：
1. 先修改 docs/sql/schema.sql，再修改 architecture.md；
2. 按现有分层新增 entity、mapper、service、controller、dto/vo；
3. 知识库资源关联必须支持 NOTE 和 DOCUMENT；
4. 列表、详情、上传、删除、重索引都要检查知识库权限；
5. 增加最小 API：创建知识库、列表、详情、添加成员、移除成员、加入资料；
6. 增加越权测试：VIEWER 不能删除、非成员返回 403、OWNER 可以管理成员；
7. 保持现有 /api/notes 和 /api/documents 接口兼容，必要时通过新接口逐步迁移，不要直接删除旧接口。

先输出数据库和接口设计，确认后再修改代码。
```

## 五、阶段 3：稳定索引状态

```text
执行阶段 3：完善现有 Markdown 文件和笔记的索引状态，不重写索引流水线。

请检查已有：
- IndexingServiceImpl；
- MarkdownChunker；
- QianwenEmbeddingClient；
- MilvusVectorStore；
- document_chunk；
- note/document 的 index_status、index_error、content_version 字段。

目标：
- 上传或正文变更后为 PENDING；
- 索引成功为 INDEXED；
- 失败为 FAILED 并保存原因；
- 失败可以手动重试；
- 删除资料时清理数据库切片和 Milvus 向量；
- 索引任务不能绕过知识库权限。

要求：
1. 优先补测试，不要先改架构；
2. 使用 FakeEmbeddingClient 或测试替身，不依赖真实 API Key；
3. 验证同一资料重复索引不会产生错误版本或重复向量；
4. 验证删除资料后无法继续检索到对应向量；
5. 前端只补充必要的状态展示和重试按钮。
```

## 六、阶段 4：实现最小知识库问答

```text
执行阶段 4：实现最小可用的知识库问答闭环。

不要先做多模型、流式输出、复杂混合检索或聊天历史。先完成一次问答：

问题 → 生成查询向量 → 按知识库和权限过滤 → Milvus Top-K → 生成回答 → 返回引用。

建议接口：
POST /api/knowledge-bases/{id}/query

返回至少包含：
- answer；
- citations：resourceId、resourceType、chunkId、content、score。

权限要求：
- OWNER、EDITOR、VIEWER 可以在所属知识库提问；
- 非成员返回 403；
- 检索必须限制在当前 knowledge_base_id；
- 不能因为知道 resourceId 或 chunkId 就访问其他知识库内容。

实现要求：
1. 先阅读现有 EmbeddingClient 和 VectorStore 接口，复用它们；
2. 如果当前 VectorStore 缺少按知识库过滤的字段，先设计最小兼容修改；
3. 模型调用封装为独立接口，增加 FakeAnswerClient 供测试使用；
4. 先完成后端接口和集成测试，再写前端问答页面；
5. 前端展示回答、引用文件名和引用片段；
6. 外部模型未配置时，返回明确错误，不要静默伪造生产答案。
```

## 七、给 VS Code AI 的纠偏提示词

如果 AI 开始重写项目、忽略现有代码，发送：

```text
暂停修改。你没有遵守“基于现有代码增量开发”的要求。
请重新执行：先读取 git status、相关 Controller、Service、Mapper、Entity、schema.sql 和现有测试；列出哪些能力已经存在；指出你准备复用的类和接口；只提出最小 diff，不要创建重复模块，不要删除未提交修改。等待我确认后再编辑。
```

如果 AI 只做前端、不做后端权限检查，发送：

```text
这个功能涉及权限，不能只在前端隐藏按钮。请补充后端 Controller/Service 的授权检查，并增加未登录 401、无权限 403、越权访问 403 的测试。前端显示控制只能作为体验优化，不能作为安全边界。
```

如果 AI 发现文档和代码不一致，发送：

```text
以实际源码和测试为准，不要直接按 README 重写。请列出文档与代码的差异，优先保证当前代码可运行，再单独更新 README 和 architecture.md，保留已有功能说明。
```

## 八、使用顺序

```text
总控提示词
  ↓
阶段 0：稳定基线
  ↓
阶段 1：ROLE_USER / ROLE_ADMIN RBAC 闭环
  ↓
阶段 2：知识库和 OWNER / EDITOR / VIEWER
  ↓
阶段 3：稳定索引
  ↓
阶段 4：最小知识库问答
  ↓
阶段 5：标签、搜索、分享、主题等扩展
```

每次只发一个阶段提示词。AI 修改后先看 diff 和测试结果，再进入下一阶段。

## 九、GitHub 分支协作规则

本项目采用：个人分支开发 → push 到 GitHub → 组长审核 → 组长决定是否合并。

把下面规则追加到总控提示词中：

```text
Git 协作规则：
1. 我当前在个人功能分支上开发，不要切换到 main 或其他成员分支。
2. 不要直接向 main push，也不要执行 merge、rebase 或 force push，除非我明确要求。
3. 每个阶段保持一个清晰的功能边界，使用独立且可审查的提交。
4. 提交前必须检查 git diff、git status，并运行与本阶段相关的测试。
5. 提交信息使用中文 Conventional Commit 格式，例如：
   feat: 增加知识库成员权限
   fix: 修复普通用户越权访问
   test: 增加管理员接口权限测试
6. 不要提交临时日志、IDE 配置、密钥、.env、本地数据库文件或构建产物。
7. 如果发现工作区已有未提交修改，先列出这些修改，不要覆盖或重置它们。
8. 默认只修改本地工作区，不自动 commit 或 push；只有我明确要求时才创建提交。
9. 只有在我明确说“push 这个分支”后，才执行 push 前检查并给出目标远程分支。
10. push 前必须汇报：当前分支、提交列表、测试结果、将要 push 的远程分支。
11. 组长审核需要的信息包括：改动目的、主要文件、API 变化、数据库变化、测试命令和已知风险。
```

### 推荐的本地操作顺序

```powershell
# 确认当前分支和工作区
git branch --show-current
git status --short

# 完成一个阶段后查看改动
git diff --stat
git diff

# 运行测试后提交
git add <本阶段相关文件>
git commit -m "feat: 完成某个明确功能"

# 检查提交内容
git show --stat --oneline HEAD

# 用户确认后再推送个人分支
git push -u origin <你的分支名>
```

### 发给组长审核的说明模板

```markdown
## 本次改动
- 完成：
- 未完成：

## 主要文件
- `backend/...`
- `frontend/...`
- `docs/sql/schema.sql`

## 接口或数据库变化
- 新增接口：
- 修改表：

## 验证方式
- `backend\\mvnw.cmd test`
- `npm --prefix frontend run test -- --run`
- `npm --prefix frontend run build`

## 已知问题
- 
```

