# 后端工程

Spring Boot 2.7 + MyBatis-Plus + MySQL + Redis + Sa-Token + 腾讯云 COS + WebSocket。

## 启动

```bash
# 1. 准备 MySQL 与 Redis（参考根 README）

# 2. 拷贝配置
cd src/main/resources
cp application-template.yml application.yml
# 编辑 application.yml 替换所有 your-xxx 占位符

# 3. 启动
cd ../../
mvn spring-boot:run
```

启动后：
- HTTP 端口：`8123`
- 接口前缀：`/api`
- 接口文档：`http://localhost:8123/api/doc.html`

## 模块划分

```
src/main/java/com/zkxpicturebackend/
├── annotation/         # 自定义注解（如 @AuthCheck）
├── api/                # 第三方接口封装（图像搜索、COS 客户端）
├── aop/                # 切面（鉴权、日志）
├── config/             # Web / Redis / Sa-Token / Knife4j 配置
├── constant/           # 公共常量
├── conroller/          # Controller 层（注意项目历史拼写：conroller）
├── exception/          # 全局异常处理
├── manager/            # 通用 Manager（文件上传、缓存等）
├── model/              # 数据模型（实体 / VO / DTO / 请求/响应）
├── service/            # Service 层（含实现类）
└── utils/              # 工具类
```

## 主要接口

> 完整接口列表见 knife4j 文档：`http://localhost:8123/api/doc.html`

| 模块 | Controller | 关键接口 |
|---|---|---|
| 用户 | `UserController` | `/user/register`、`/user/login`、`/user/add`、`/user/delete`、`/user/get`、`/user/list` |
| 图片 | `PictureController` | `/picture/upload`、`/picture/delete`、`/picture/edit`、`/picture/list/page/vo` |
| 文件 | `FileController` | `/file/upload`（支持 COS 预签名直传） |
| 空间 | `SpaceController` | `/space/add`、`/space/edit`、`/space/level` |
| 空间成员 | `SpaceUserController` | `/spaceUser/add`、`/spaceUser/edit`、`/spaceUser/delete`、`/spaceUser/list` |
| AI 标签 | `AiController` | `/ai/imageTagging`（通义千问） |
| 以图搜图 | `PictureController.getImagePageUrl` | 跳转百度图像搜索 |

## 异步事件

- **RabbitMQ**：图片上传后 AI 打标异步化（生产者 `AiTagProducer` → 队列 → 消费者 `AiTagConsumer` 调用通义千问 VL），手动 ack + 自动重试（3 次）
- **Disruptor**：无锁内存队列，处理高并发事件分发
- **WebSocket**：审核结果、空间成员变更实时推送给在线成员

## 缓存策略

- **Caffeine**（JVM 内 LRU，TTL 10 分钟）：存储热点 VO（如首页推荐图片列表）
- **Redis**（跨实例共享）：Session、限流、分布式锁、COS 预签名缓存

## 测试

项目使用 IDEA HTTP Client：

- `httpTest/httpTest/picture.http`：图片相关接口
- `httpTest/httpTest/space_user.http`：空间成员管理
- `httpTest/httpTest/http-client.env.template.json`：环境变量模板（**真实 cookie 已脱敏，请拷贝为 `http-client.env.json` 后填入自己的值**）

## 环境变量

`application-template.yml` 支持以下环境变量覆盖：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `your-database-password` | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `RABBITMQ_HOST` | `127.0.0.1` | RabbitMQ 主机（异步 AI 打标） |
| `RABBITMQ_PORT` | `5672` | RabbitMQ 端口 |
| `RABBITMQ_USERNAME` | `your-rabbitmq-username` | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | `your-rabbitmq-password` | RabbitMQ 密码 |
| `COS_HOST` | `https://your-bucket.cos...` | COS 访问域名 |
| `COS_SECRET_ID` | `AKIDxxxx...` | 腾讯云 API 密钥 ID |
| `COS_SECRET_KEY` | `your-cos-secret-key-here` | 腾讯云 API 密钥 Key |
| `COS_REGION` | `ap-guangzhou` | COS 地域 |
| `COS_BUCKET` | `your-bucket-name` | COS 存储桶名称 |
| `ALIYUN_AI_API_KEY` | `sk-xxxx...` | 阿里云百炼 API Key |

## 已知技术债（面试 / 后续优化方向）

- 密码使用 MD5 + 静态盐，**应升级 BCrypt**
- 密钥目前明文写在 `application.yml`，**应迁移到环境变量 / 密钥管理服务（KMS）**
- 用户删除未级联处理空间 / 空间成员，会产生孤儿数据
- 计划引入 **ShardingSphere** 做图片表水平拆分
