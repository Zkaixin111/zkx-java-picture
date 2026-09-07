# 前端工程

Vue 3 + Vite 6 + Pinia + Vue Router 4 + Ant Design Vue 4 + ECharts + TypeScript。

## 开发命令

```bash
# 安装依赖
npm install

# 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 生产构建（输出到 dist/）
npm run build

# 类型检查 + 构建
npm run build-check

# ESLint 检查与修复
npm run lint
```

## 目录约定

```
src/
├── access.ts          # 路由权限辅助
├── api/               # 与后端对接的请求函数（由 openapi 自动生成，可手写）
├── components/        # 通用组件
├── constants/         # 常量
├── layouts/           # 布局组件（BasicLayout 等）
├── pages/             # 页面，按功能划分（Home / Picture / Space / Admin 等）
├── router/            # Vue Router 配置
├── stores/            # Pinia 状态
├── utils/             # 工具函数
└ ├── App.vue
```

## 环境变量

前端默认通过 Vite 代理把 `/api` 转发到 `http://localhost:8123/api`。如需修改，在项目根目录新建 `.env.local`：

```bash
# .env.local
VITE_API_BASE_URL=http://your-backend:8123/api
```

读取方式：`import.meta.env.VITE_API_BASE_URL`

## 后端对接

- 默认请求基地址：`/api`（已配置 Vite 代理）
- 鉴权：浏览器 cookie + request header（`satoken`），由 Sa-Token 自动管理
- 错误处理：统一在 `src/utils/request.ts` 中处理 `BaseResponse` 包装

## 类型生成

`openapi.config.js` 会在 `npm run dev` 启动时拉取后端 OpenAPI 文档，自动生成 `src/api/` 下的 TypeScript 类型与请求函数。

> 请确保后端已在 `http://localhost:8123/api/v2/api-docs` 提供 OpenAPI JSON。

## 推荐 IDE

[VSCode](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（替代旧版 Volar，并禁用 Vetur）。
