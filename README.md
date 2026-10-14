# SmartCampus 校园智能问答系统

一个用于学习和演示的校园问答项目：学生在网页中提问，后端按意图调用教务查询工具、检索知识库或创建人工服务工单。知识库使用 Parent–Child 分块、Milvus 向量检索、MySQL BM25 检索与 FAQ 匹配。

> 本仓库仅包含源码和空表结构，不包含原项目的用户、聊天记录、个人文档、上传文件、模型密钥或演示账号。请使用自有且获授权的知识库材料。

## 技术栈

- 后端：Java 17、Spring Boot 3.3.5、Spring Security、JWT、MyBatis-Plus
- 前端：Vue 3、Vite、Pinia、Element Plus、Axios
- 存储与检索：MySQL 8、Redis 7、Milvus、MinIO
- 模型：OpenAI 兼容的聊天接口；阿里云百炼 Embedding/Rerank 接口

项目通过自定义 HTTP 客户端访问模型；`pom.xml` 中的 Spring AI 依赖目前处于注释状态。

## 目录

```text
src/main/java/             后端接口、业务服务、检索与入库代码
src/main/resources/        应用配置及空表结构
src/test/java/             后端测试
campus-frontend/src/       前端页面、路由与 API 客户端
docker-compose.yml         本地开发用的五个依赖服务
.env.example               所需环境变量示例
```

## 本地启动

需要 Docker、JDK 17 或更新版本、Maven 3.9+、Node.js 20+ 和 npm。模型问答需要聊天模型 API Key；知识库入库及语义检索还需要阿里云百炼 Embedding/Rerank Key。首次启动需要下载 Docker 镜像和 Maven/npm 依赖。

1. 复制 `.env.example` 为 `.env`，设置 `DB_PASSWORD`、`REDIS_PASSWORD`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。用 `docker compose up -d` 启动 MySQL、Redis、etcd、MinIO、Milvus。数据库首次创建时会导入 `schema.sql` 的空表结构。已有数据库卷不会重新导入。
2. 在启动后端的终端设置同名环境变量，并额外设置 `JWT_SECRET`、`LLM_API_KEY`、`LLM_BASE_URL`、`DASHSCOPE_EMBED_API_KEY`。`.env` 由 Docker Compose 读取，Spring Boot **不会自动读取**它。
3. 在仓库根目录运行 `mvn spring-boot:run`，后端监听 `http://localhost:8080`。
4. 在 `campus-frontend` 目录运行 `npm ci` 和 `npm run dev`，打开 `http://localhost:5173`。Vite 将 `/api` 和 `/ws` 转发到本地后端。

PowerShell 设置变量示例（请替换为自己的值）：

```powershell
$env:DB_PASSWORD = 'your-db-password'
$env:REDIS_PASSWORD = 'your-redis-password'
$env:MINIO_ACCESS_KEY = 'your-minio-user'
$env:MINIO_SECRET_KEY = 'your-minio-password'
$env:JWT_SECRET = 'use-a-long-random-secret-at-least-32-bytes'
$env:LLM_API_KEY = 'your-chat-model-key'
$env:LLM_BASE_URL = 'https://api.deepseek.com'
$env:DASHSCOPE_EMBED_API_KEY = 'your-dashscope-key'
mvn spring-boot:run
```

`DB_PASSWORD` 等值必须与 `.env` 中供 Docker Compose 使用的值一致。聊天接口配置与 Embedding/Rerank 接口分别使用不同的 Key；只配聊天模型时，知识库入库可能失败。

## 主要流程

1. `POST /api/v1/auth/register` 注册普通学生账号；`POST /api/v1/auth/login` 登录并取得 JWT。教师及管理员账号需要由可信的管理流程创建，公开注册不授予这些角色。
2. 前端通过 `GET /api/v1/chat/stream` 接收 SSE 回答。后端先合并会话上下文，再按意图进入闲聊、教务工具、人工工单或 RAG 问答。
3. 教师或管理员通过 `POST /api/v1/knowledge/docs/upload` 上传文档。原文件进入 MinIO；异步任务解析内容、写入 MySQL 父子块及 BM25 索引，并把向量写入 Milvus。
4. RAG 查询先检查 FAQ，再进行向量和 BM25 召回、RRF 融合、重排和父块回捞，最后生成答案与来源信息。
5. `/api/v1/tickets` 提供人工工单操作，`/topic/tickets` 推送状态更新；`/api/v1/settings/configs` 管理运行时配置。

## 测试

```bash
mvn test
cd campus-frontend
npm run build
```

## 安全与使用边界

- 本仓库不提供真实学校数据或默认密码。不要把 `.env`、数据库导出、上传目录、日志或真实用户材料提交到 Git。
- 原项目的模型 API Key、JWT 密钥及中间件口令曾被写入本地配置；如果这些值仍有效，原持有人应在对应服务中撤销并轮换。删除公开副本中的值不能使旧密钥失效。
- 自助找回密码入口已停用，因为原实现只凭用户名和邮箱设置固定密码；部署者应接入带一次性令牌的验证流程。
- 这是教学项目，当前的会话、工单、图片和 WebSocket 授权边界仍需进一步加固。不要直接用于处理真实校园个人信息或暴露在公网。

## 许可证

本项目采用 [MIT License](LICENSE)。
