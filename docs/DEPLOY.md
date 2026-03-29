# Mirad 文件上传系统部署教程

本文按公网 IP `121.41.1.134`、对外端口 `9090` 编写，默认系统为 Ubuntu 22.04。

## 一、服务器环境要求

- JDK：`17`
- Maven：`3.9+`
- Node.js：`18 LTS` 或 `20 LTS`
- npm：随 Node 安装
- Nginx：`1.20+`

## 二、推荐部署方式

- 前端 Vue 打包为静态文件
- 后端 Spring Boot 打包为 JAR
- Spring Boot 只监听服务器内网 `127.0.0.1:8081`
- Nginx 对外监听 `121.41.1.134:9090`
- 浏览器统一访问：`http://121.41.1.134:9090`

这样部署最稳，前端和后端最终同源访问，生产环境无需额外处理浏览器跨域问题。

## 三、服务器安装依赖

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven nginx curl
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node -v
npm -v
java -version
mvn -v
nginx -v
```

## 四、上传项目到服务器

假设项目放到：

- 项目目录：`/opt/mirad-upload`
- 后端运行目录：`/opt/mirad-upload/backend`
- 前端运行目录：`/opt/mirad-upload/frontend`
- 数据目录：`/opt/mirad-upload/data`

```bash
sudo mkdir -p /opt/mirad-upload
sudo chown -R $USER:$USER /opt/mirad-upload
```

把整个项目上传到服务器后进入项目目录：

```bash
cd /opt/mirad-upload
```

## 五、前端打包步骤

```bash
cd /opt/mirad-upload/frontend
npm install
npm run build
```

打包完成后会生成：

```bash
/opt/mirad-upload/frontend/dist
```

## 六、后端打包步骤

```bash
cd /opt/mirad-upload/backend
mvn clean package -DskipTests
```

打包完成后会生成：

```bash
/opt/mirad-upload/backend/target/mirad-upload-backend-1.0.0.jar
```

## 七、后端启动命令

先创建数据目录：

```bash
mkdir -p /opt/mirad-upload/data
```

临时启动命令：

```bash
cd /opt/mirad-upload/backend
nohup java \
  -jar target/mirad-upload-backend-1.0.0.jar \
  --server.address=127.0.0.1 \
  --server.port=8081 \
  --mirad.data-dir=/opt/mirad-upload/data \
  > /opt/mirad-upload/backend/backend.log 2>&1 &
```

查看是否启动：

```bash
ss -lntp | grep 8081
tail -f /opt/mirad-upload/backend/backend.log
```

停止临时启动的后端：

```bash
ps -ef | grep mirad-upload-backend-1.0.0.jar
kill -9 后端进程PID
```

## 八、配置 systemd 常驻运行

复制服务文件：

```bash
sudo cp /opt/mirad-upload/deploy/systemd/mirad-upload-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable mirad-upload-backend
sudo systemctl start mirad-upload-backend
```

常用命令：

```bash
sudo systemctl status mirad-upload-backend
sudo systemctl restart mirad-upload-backend
sudo systemctl stop mirad-upload-backend
sudo journalctl -u mirad-upload-backend -f
```

## 九、配置 Nginx 监听 9090

复制 Nginx 配置：

```bash
sudo cp /opt/mirad-upload/deploy/nginx/mirad-upload.conf /etc/nginx/conf.d/mirad-upload.conf
sudo nginx -t
sudo systemctl restart nginx
```

开放防火墙端口：

```bash
sudo ufw allow 9090/tcp
```

## 十、跨域配置方案

### 生产环境方案

生产环境使用 Nginx 反向代理，浏览器只访问：

```text
http://121.41.1.134:9090
```

Nginx 会把 `/api/*` 代理到后端 `127.0.0.1:8081`，因此前后端同源，不会产生浏览器跨域问题。

### 开发环境方案

开发时前端运行在 `8080`，后端运行在 `8081`：

- 前端通过 `vue.config.js` 代理 `/api`
- 后端已在 `application.yml` 中放开 `http://localhost:8080`、`http://127.0.0.1:8080`、`http://121.41.1.134:9090`

如果后续你需要增加来源地址，只要修改：

```text
backend/src/main/resources/application.yml
```

中的 `mirad.cors-allowed-origins` 即可。

## 十一、验证是否部署成功

### 1. 验证后端健康状态

浏览器打开：

```text
http://121.41.1.134:9090/api/auth/session
```

未登录时应返回类似：

```json
{"loggedIn":false}
```

### 2. 验证前端页面

浏览器打开：

```text
http://121.41.1.134:9090
```

检查点：

- 页面可以正常打开
- 登录弹窗正常显示
- 登录后可上传文件
- 文件列表可刷新
- 文件可预览、下载、删除

### 3. 命令行验证

```bash
curl http://121.41.1.134:9090/api/auth/session
curl -I http://121.41.1.134:9090
```

## 十二、常用运维命令

查看后端日志：

```bash
sudo journalctl -u mirad-upload-backend -n 200
sudo journalctl -u mirad-upload-backend -f
```

重启后端：

```bash
sudo systemctl restart mirad-upload-backend
```

停止后端：

```bash
sudo systemctl stop mirad-upload-backend
```

查看 Nginx 日志：

```bash
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

重启 Nginx：

```bash
sudo systemctl restart nginx
```

## 十三、最终公网访问地址

- 前端访问地址：`http://121.41.1.134:9090`
- 后端接口统一入口：`http://121.41.1.134:9090/api/...`

## 十四、你实际执行时的最短步骤

```bash
cd /opt/mirad-upload/frontend
npm install
npm run build

cd /opt/mirad-upload/backend
mvn clean package -DskipTests

sudo cp /opt/mirad-upload/deploy/systemd/mirad-upload-backend.service /etc/systemd/system/
sudo cp /opt/mirad-upload/deploy/nginx/mirad-upload.conf /etc/nginx/conf.d/mirad-upload.conf
sudo systemctl daemon-reload
sudo systemctl enable mirad-upload-backend
sudo systemctl restart mirad-upload-backend
sudo nginx -t
sudo systemctl restart nginx
```
