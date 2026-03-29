# Mirad 文件上传系统

Mirad 文件上传系统现已重构为标准前后端分离项目：

- `frontend/`：Vue 3 前端
- `backend/`：Spring Boot 3 后端
- `deploy/`：Nginx 与 systemd 部署配置
- `docs/DEPLOY.md`：服务器部署教程

仓库中已删除旧版原生 HTML 前端、旧版 Java HTTP 服务实现、旧脚本和本地编译产物，只保留当前重构后的可维护结构。

## 当前目录结构

```text
mirad-upload-main
├─ backend
│  ├─ pom.xml
│  └─ src/main
│     ├─ java/com/example/mirad
│     │  ├─ config
│     │  ├─ controller
│     │  ├─ model
│     │  ├─ repository
│     │  ├─ security
│     │  ├─ service
│     │  ├─ util
│     │  └─ web
│     └─ resources/application.yml
├─ frontend
│  ├─ package.json
│  ├─ package-lock.json
│  ├─ vue.config.js
│  ├─ public/index.html
│  └─ src
│     ├─ api
│     ├─ assets/styles
│     ├─ utils
│     ├─ App.vue
│     └─ main.js
├─ deploy
│  ├─ nginx/mirad-upload.conf
│  └─ systemd/mirad-upload-backend.service
├─ docs
│  └─ DEPLOY.md
├─ .gitignore
└─ README.md
```

## 本地启动

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8081`

### 启动前端

```bash
cd frontend
npm install
npm run serve
```

默认地址：`http://127.0.0.1:8080`

开发环境下前端会通过 [frontend/vue.config.js](/G:/mirad/mirad-upload-main/frontend/vue.config.js) 自动代理 `/api` 到后端 `8081`。

## 打包命令

### 前端

```bash
cd frontend
npm install
npm run build
```

### 后端

```bash
cd backend
mvn clean package -DskipTests
```

## 核心兼容性

- 保留登录、注册、会话检查、上传、列表、预览、下载、删除
- 保留原接口路径：`/api/auth/*`、`/api/files/*`
- 保留原请求方式：`GET`、`POST`、`DELETE`
- 保留原参数名：`username`、`password`、`files`
- 保留原返回字段：`success`、`message`、`loggedIn`、`username`、`files`
- 保留原页面布局、样式和交互风格

## 部署文档

详细部署请查看 [docs/DEPLOY.md](/G:/mirad/mirad-upload-main/docs/DEPLOY.md)。
