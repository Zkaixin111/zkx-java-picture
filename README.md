# 拾光云图（Picture Cloud）

一个面向团队的云图库系统，支持图片上传、AI 智能打标、以图搜图、按颜色找图、团队空间协作、实时消息推送（WebSocket）等能力。

- 后端：Spring Boot 2.7 + MyBatis-Plus + MySQL + Redis + Sa-Token + 腾讯云 COS 数据万象 + WebSocket + Disruptor + Caffeine
- 前端：Vue 3 + Vite 6 + Pinia + Vue Router 4 + Ant Design Vue 4 + ECharts + TypeScript

> 本项目演示用，所有真实的密钥、账号、cookie、空间 ID 已脱敏为占位符；上传到 GitHub 前请参考下方「部署运行」章节自行替换。

---

## 一、功能概览

| 模块 | 说明 |
|---|---|
| 用户体系 | 邮箱注册、登录、Session+Sa-Token 双轨鉴权、管理员加用户 |
| 图片管理 | 上传、批量上传、按主色调/标签/名称搜索、编辑、删除、批量操作 |
| AI 能力 | 阿里云通义千问打标签、以图搜图（百度图像搜索）、按主色相似度排序 |
| 空间与权限 | 私有空间（个人）、企业空间（团队）三级 RBAC（admin/editor/viewer） |
| 实时消息 | WebSocket 推送图片审核结果、空间成员变更 |
| 性能优化 | Caffeine 本地缓存 + Redis 分布式缓存、Disruptor 异步队列、对象存储直传 |
| 接口文档 | knife4j（Swagger UI）聚合所有 Controller |

## 二、技术栈

**后端**
- Spring Boot 2.7.6（JDK 1.8）
- MyBatis-Plus 3.5.9
- MySQL 5.7+ / Redis 5+
- Sa-Token 1.39（鉴权）
- knife4j 4.x（接口文档）
- 腾讯云 COS + 数据万象（存储 + 图片处理）
- WebSocket + Disruptor（异步事件）
- Caffeine（本地缓存）

**前端**
- Vue 3.5 + Vite 6 + TypeScript
- Pinia / Vue Router 4 / Ant Design Vue 4
- ECharts（图表）
- OpenAPI 自动生成前端 API 客户端

## 三、目录结构

```
.
├── README.md                          # 本文件（总入口）
├── .gitignore                         # 全局忽略规则
├── zkx-picture-backend/               # 后端工程
│   ├── README.md                      # 后端模块说明
│   └── zkx-picture/                   # 主模块
│       ├── pom.xml
│       ├── sql.create_table_sql.sql    # 建库建表脚本
│       ├── src/main/java/             # 业务代码
│       ├── src/main/resources/        # application-template.yml 等
│       └── httpTest/                  # IDEA HTTP Client 测试用例
└── zkx-picture-frontend/              # 前端工程
    ├── README.md                      # 前端开发说明
    ├── package.json
    ├── src/
    └── openapi.config.js              # openapi 自动生成配置
```

## 四、环境要求

| 软件 | 版本 | 用途 |
|---|---|---|
| JDK | 1.8+ | 后端编译运行 |
| Maven | 3.6+ | 后端依赖管理 |
| MySQL | 5.7+ / 8.0 | 主数据库 |
| Redis | 5.0+ | Session / 缓存 |
| Node.js | 18+ | 前端开发与构建 |
| 腾讯云 COS | 任意 region | 对象存储 |
| 阿里云百炼 / DashScope | - | AI 打标（可选） |

## 五、部署运行

### 1. 初始化数据库

```bash
# 登录 MySQL
mysql -u root -p

# 执行建库脚本（默认库名 picture_db，可在脚本顶部自行修改）
mysql> source /your/path/zkx-picture-backend/zkx-picture/sql.create_table_sql.sql;
```

### 2. 配置后端

进入 `zkx-picture-backend/zkx-picture/src/main/resources/`，把 `application-template.yml` 拷贝为 `application.yml`：

```bash
cd zkx-picture-backend/zkx-picture/src/main/resources
cp application-template.yml application.yml
```

然后编辑 `application.yml`，替换以下占位符（**全部必填**）：

| 占位符 | 含义 | 示例 |
|---|---|---|
| `${DB_USERNAME:root}` | MySQL 用户名 | root |
| `${DB_PASSWORD:your-database-password}` | MySQL 密码 | your-real-password |
| `${REDIS_HOST:127.0.0.1}` | Redis 地址 | 127.0.0.1 |
| `${REDIS_PORT:6379}` | Redis 端口 | 6379 |
| `${COS_HOST:...}` | COS 访问域名 | `https://your-bucket-1234567890.cos.ap-guangzhou.myqcloud.com` |
| `${COS_SECRET_ID:...}` | 腾讯云 API 密钥 ID | AKIDxxxxxxxxxxxxxxxxxxxx |
| `${COS_SECRET_KEY:...}` | 腾讯云 API 密钥 Key | 在控制台→访问管理→API 密钥管理生成 |
| `${COS_REGION:ap-guangzhou}` | COS 存储桶地域 | ap-guangzhou |
| `${COS_BUCKET:...}` | COS 存储桶名称 | your-bucket-name |
| `${ALIYUN_AI_API_KEY:sk-...}` | 阿里云百炼 API Key | sk-xxxxxxxxxxxxxxxxxxxx |

> 也可以不修改 `application.yml`，通过环境变量注入：`DB_PASSWORD=xxx COS_SECRET_ID=xxx java -jar app.jar`。

### 3. 启动后端

```bash
cd zkx-picture-backend/zkx-picture
mvn spring-boot:run
```

- 默认端口：`8123`
- 接口前缀：`/api`
- 启动后访问 `http://localhost:8123/api/doc.html` 查看 knife4j 接口文档

### 4. 启动前端

```bash
cd zkx-picture-frontend
npm install
npm run dev
```

默认监听 `http://localhost:5173`，首次启动会自动跳到登录页。

> 前端通过 Vite 代理把 `/api` 转发到后端 `8123`，无需额外配置 CORS。

## 六、基本使用流程

1. **注册账号**：访问 `/user/register`，填邮箱、密码、确认密码（≤ 16 位、含数字和字母）
2. **登录**：进入 `/user/login`，登录成功后会跳到首页 `/`
3. **上传图片**：点击右上角"创建图片"→ 选择本地文件（≤ 10MB）→ 填写名称、简介、标签 → 提交
4. **AI 打标签**：上传时勾选"AI 智能打标"，系统会调用通义千问自动补充标签
5. **创建空间**：进入"空间"页 → 创建私有空间或企业空间
6. **邀请成员**：在空间详情里添加成员，分配 admin / editor / viewer 角色
7. **以图搜图**：打开任一公开图片 → 点击"以图搜图"，会跳转到第三方图像搜索结果
8. **按主色找图**：图片详情页提取主色调，进入"找相似"页可按色值找图

## 七、HTTP 测试用例

后端工程内置了 IDEA HTTP Client 测试文件：

- `zkx-picture-backend/zkx-picture/httpTest/httpTest/picture.http`
- `zkx-picture-backend/zkx-picture/httpTest/httpTest/space_user.http`
- `zkx-picture-backend/zkx-picture/httpTest/httpTest/http-client.env.template.json`（**请拷贝为** `http-client.env.json` 后填入你自己的 session/satoken）

## 八、常见问题

- **启动报错 "Failed to configure a DataSource"** → 检查 `application.yml` 中数据库地址、用户名、密码、库名是否正确
- **上传图片失败** → 检查 COS 密钥、地域、bucket 名是否匹配，且 bucket 已开启公有读或使用预签名 URL
- **AI 打标返回 401** → 阿里云百炼 API Key 已过期或余额不足
- **WebSocket 连接断开** → 检查反向代理是否放行了 `/api/websocket` 路径，且允许长连接

## 九、安全与脱敏声明

本仓库**不包含任何真实的密钥、cookie、数据库密码或个人信息**。所有敏感字段都使用环境变量占位符或形如 `your-xxx` 的示例值。请使用者：

1. 切勿在生产环境保留 `application-template.yml` 的默认值
2. 切勿把含真实密钥的 `application.yml` 提交到 Git
3. 定期轮换腾讯云 / 阿里云的 API 密钥

## 十、License

本项目仅供学习交流使用。
