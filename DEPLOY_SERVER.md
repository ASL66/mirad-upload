# Mirad 服务器部署说明

这份文档是按照当前已经跑通的服务器方案整理的，适用于你的 Mirad 文件上传系统。

当前对外访问地址：

- `http://121.41.1.134:9090`

当前服务器登录方式：

- 推荐使用你本机已经配置好的 SSH 别名：`ssh mirad-server`

## 1. 当前部署结构

这套部署采用的是标准、稳定、便于维护的前后端分离方案：

- Vue 前端打包成静态文件
- Spring Boot 后端打成单个 JAR
- Nginx 对外监听 `9090`
- Spring Boot 只监听本机 `127.0.0.1:8081`
- 浏览器统一访问 Nginx

这样做的好处：

- 前后端最终同源访问，不容易碰到生产环境跨域问题
- 后端不直接暴露在公网，安全性更高
- Nginx 更适合提供静态文件和做反向代理
- 502、权限、端口这类问题更容易排查

## 2. 项目应该放在哪里

项目代码目录：

- `/opt/mirad-upload`

原因：

- `/opt` 是 Linux 上放第三方应用程序的常见目录
- 代码和系统数据分离，结构清楚
- 后续更新代码、回滚版本、重新打包都方便

项目目录建议结构：

```text
/opt/mirad-upload
├─ frontend
├─ backend
├─ deploy
├─ docs
├─ README.md
└─ start.sh
```

其中：

- 前端源码：`/opt/mirad-upload/frontend`
- 后端源码：`/opt/mirad-upload/backend`
- 前端打包产物：`/opt/mirad-upload/frontend/dist`
- 后端打包产物：`/opt/mirad-upload/backend/target/mirad-upload-backend-1.0.0.jar`

## 3. 用户上传的文件放在哪里

当前线上数据目录：

- `/var/lib/mirad-upload`

具体结构：

- 用户上传文件：`/var/lib/mirad-upload/uploads`
- 用户账号文件：`/var/lib/mirad-upload/users`

更具体一点：

- 某个用户的文件保存在 `/var/lib/mirad-upload/uploads/<用户名>/`
- 用户账号信息保存在 `/var/lib/mirad-upload/users/*.user`

为什么放这里：

- `/var/lib` 是 Linux 上存放应用持久化数据的标准目录
- 代码目录 `/opt/mirad-upload` 更新、替换、重新上传时，不会误伤业务数据
- 备份时只需要重点备份 `/var/lib/mirad-upload`
- 权限控制也更清晰，代码和数据分开管理

## 4. 哪些文件要上传到服务器

推荐做法：直接上传整个项目目录，但不要上传本地生成产物。

建议上传这些内容：

- `frontend/`
- `backend/`
- `deploy/`
- `start.sh`
- `README.md`
- `DEPLOY_SERVER.md`

不要上传这些本地产物：

- `frontend/node_modules`
- `frontend/dist`
- `backend/target`

原因：

- `node_modules` 和 `target` 体积大，而且和服务器环境相关
- 在服务器本机重新安装依赖和打包更稳
- 能避免把本地系统差异带到服务器

## 5. 服务器需要安装什么

系统环境建议：

- Ubuntu 22.04 或 24.04
- JDK 17
- Maven 3.9+
- Node.js 18 LTS 或 20 LTS
- npm
- Nginx

安装命令：

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven nginx curl
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

检查版本：

```bash
java -version
mvn -v
node -v
npm -v
nginx -v
```

## 6. 怎么把项目传到服务器

### 方法 A：直接上传整个项目目录

在你本机的项目根目录执行：

```powershell
scp -r .\backend .\frontend .\deploy .\start.sh .\README.md .\DEPLOY_SERVER.md mirad-server:/opt/mirad-upload/
```

如果 `/opt/mirad-upload` 不存在，先在服务器创建：

```bash
sudo mkdir -p /opt/mirad-upload
sudo chown -R root:root /opt/mirad-upload
```

### 方法 B：后续更新时重新上传变更

还是在项目根目录执行：

```powershell
scp -r .\backend .\frontend .\deploy .\start.sh mirad-server:/opt/mirad-upload/
```

上传完成后在服务器检查：

```bash
ls -la /opt/mirad-upload
```

## 7. 服务器上哪些系统配置文件要放到哪里

项目目录里的部署文件只是模板，真正生效的是系统目录中的文件。

### Nginx 配置文件

项目内模板：

- `/opt/mirad-upload/deploy/nginx/mirad-upload.conf`

系统生效位置：

- `/etc/nginx/conf.d/mirad-upload.conf`

复制命令：

```bash
sudo cp /opt/mirad-upload/deploy/nginx/mirad-upload.conf /etc/nginx/conf.d/mirad-upload.conf
```

### systemd 服务文件

这次线上真正使用的服务名是：

- `mirad-upload`

系统生效位置：

- `/etc/systemd/system/mirad-upload.service`

注意：

- 不要让 systemd 直接执行根目录的 `start.sh`
- `start.sh` 适合手动更新部署
- systemd 应该直接启动后端 JAR

正确的服务文件内容如下：

```ini
[Unit]
Description=Mirad Upload Service
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/mirad-upload/backend
Environment=SERVER_ADDRESS=127.0.0.1
Environment=SERVER_PORT=8081
Environment=MIRAD_DATA_DIR=/var/lib/mirad-upload
Environment=MIRAD_COOKIE_SECURE=false
Environment=MIRAD_SESSION_TIMEOUT=60m
ExecStart=/usr/bin/java -jar /opt/mirad-upload/backend/target/mirad-upload-backend-1.0.0.jar
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/var/lib/mirad-upload /opt/mirad-upload/backend
UMask=027
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

写入命令：

```bash
sudo tee /etc/systemd/system/mirad-upload.service > /dev/null <<'EOF'
[Unit]
Description=Mirad Upload Service
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/mirad-upload/backend
Environment=SERVER_ADDRESS=127.0.0.1
Environment=SERVER_PORT=8081
Environment=MIRAD_DATA_DIR=/var/lib/mirad-upload
Environment=MIRAD_COOKIE_SECURE=false
Environment=MIRAD_SESSION_TIMEOUT=60m
ExecStart=/usr/bin/java -jar /opt/mirad-upload/backend/target/mirad-upload-backend-1.0.0.jar
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/var/lib/mirad-upload /opt/mirad-upload/backend
UMask=027
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
```

## 8. 首次部署步骤

### 第 1 步：准备目录

```bash
sudo mkdir -p /opt/mirad-upload
sudo mkdir -p /var/lib/mirad-upload/uploads
sudo mkdir -p /var/lib/mirad-upload/users
sudo chown -R www-data:www-data /var/lib/mirad-upload
```

说明：

- `/opt/mirad-upload` 放代码
- `/var/lib/mirad-upload` 放业务数据
- `www-data` 需要对业务数据目录有写权限

### 第 2 步：上传项目

在本机执行：

```powershell
scp -r .\backend .\frontend .\deploy .\start.sh .\README.md .\DEPLOY_SERVER.md mirad-server:/opt/mirad-upload/
```

### 第 3 步：安装前端依赖并打包

```bash
cd /opt/mirad-upload/frontend
npm install
npm run build
```

打包结果会生成到：

- `/opt/mirad-upload/frontend/dist`

### 第 4 步：打包后端

```bash
cd /opt/mirad-upload/backend
mvn clean package -DskipTests
```

打包结果：

- `/opt/mirad-upload/backend/target/mirad-upload-backend-1.0.0.jar`

### 第 5 步：修正产物权限

```bash
sudo chown -R www-data:www-data /opt/mirad-upload/backend/target
sudo chmod -R a+rX /opt/mirad-upload/frontend/dist
sudo chmod -R a+rX /opt/mirad-upload/backend/target
```

原因：

- Nginx 需要读取 `frontend/dist`
- `www-data` 启动的后端服务需要读取 JAR

### 第 6 步：安装 systemd 服务

把上面那段 `mirad-upload.service` 内容写入后执行：

```bash
sudo systemctl daemon-reload
sudo systemctl enable mirad-upload
sudo systemctl restart mirad-upload
```

### 第 7 步：安装 Nginx 配置

```bash
sudo cp /opt/mirad-upload/deploy/nginx/mirad-upload.conf /etc/nginx/conf.d/mirad-upload.conf
sudo nginx -t
sudo systemctl reload nginx
```

### 第 8 步：放行端口

如果开启了 UFW：

```bash
sudo ufw allow 9090/tcp
```

## 9. 日常更新怎么做

如果你修改了代码，推荐流程是：

### 第 1 步：重新上传源码

```powershell
scp -r .\backend .\frontend .\deploy .\start.sh mirad-server:/opt/mirad-upload/
```

### 第 2 步：登录服务器

```powershell
ssh mirad-server
```

### 第 3 步：在服务器手动执行更新脚本

```bash
cd /opt/mirad-upload
chmod +x start.sh
./start.sh
```

这个脚本会：

- 构建前端
- 打包后端
- 重启 `mirad-upload` 服务

注意：

- `start.sh` 是手动更新脚本
- 不要把它配置成 systemd 的 `ExecStart`

## 10. 验证部署是否成功

### 检查 systemd 服务

```bash
sudo systemctl status mirad-upload --no-pager -l
```

### 检查 8081 是否监听

```bash
ss -lntp | grep 8081
```

### 检查后端接口

```bash
curl http://127.0.0.1:8081/api/auth/session
```

正常返回：

```json
{"loggedIn":false}
```

### 检查公网入口

```bash
curl http://127.0.0.1:9090/api/auth/session
curl -I http://127.0.0.1:9090
```

浏览器访问：

- `http://121.41.1.134:9090`

## 11. 常用运维命令

查看后端日志：

```bash
sudo journalctl -u mirad-upload -n 200 --no-pager
sudo journalctl -u mirad-upload -f
```

重启后端：

```bash
sudo systemctl restart mirad-upload
```

停止后端：

```bash
sudo systemctl stop mirad-upload
```

查看 Nginx 错误日志：

```bash
sudo tail -n 200 /var/log/nginx/error.log
sudo tail -f /var/log/nginx/error.log
```

查看 Nginx 当前生效配置引用：

```bash
sudo grep -R -n "127.0.0.1:8888\\|127.0.0.1:8081\\|listen 9090\\|mirad-upload" /etc/nginx
```

## 12. 这套部署里最容易踩的坑

### 坑 1：把 `start.sh` 当成 systemd 的启动命令

这是最容易出问题的地方。

原因：

- `start.sh` 会先构建前端、再打包后端、再重启服务
- 如果它本身又被 systemd 当作服务启动命令，就会形成逻辑冲突
- 一旦构建阶段权限不足，服务就会直接起不来

正确做法：

- `start.sh` 只手动执行
- systemd 直接跑 JAR

### 坑 2：前端 `dist` 权限不对

如果 `npm run build` 是 root 跑的，而服务是 `www-data`，有时会出现目录删除或覆盖失败。

解决方式：

```bash
sudo rm -rf /opt/mirad-upload/frontend/dist
cd /opt/mirad-upload/frontend
npm run build
sudo chmod -R a+rX /opt/mirad-upload/frontend/dist
```

### 坑 3：后端 JAR 可读但服务还是起不来

检查：

```bash
ls -l /opt/mirad-upload/backend/target
```

必要时执行：

```bash
sudo chown -R www-data:www-data /opt/mirad-upload/backend/target
sudo chmod -R a+rX /opt/mirad-upload/backend/target
```

### 坑 4：Nginx 里还有旧站点配置

你这台机器上之前就存在旧配置把请求转发到 `127.0.0.1:8888`。

如果以后又出现 502，先查：

```bash
sudo grep -R "127.0.0.1:8888" /etc/nginx
```

如果有旧站点不再使用，就删除或禁用它，再：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### 坑 5：看见 502 但不知道查哪里

按这个顺序查最快：

```bash
sudo systemctl status mirad-upload --no-pager -l
sudo journalctl -u mirad-upload -n 100 --no-pager
ss -lntp | grep 8081
curl http://127.0.0.1:8081/api/auth/session
sudo tail -n 100 /var/log/nginx/error.log
```

## 13. 备份建议

真正需要备份的是业务数据，不是代码目录。

重点备份：

- `/var/lib/mirad-upload/uploads`
- `/var/lib/mirad-upload/users`

例如：

```bash
sudo tar -czf /root/mirad-data-backup.tar.gz /var/lib/mirad-upload
```

## 14. SSH 登录说明

你本机已经配置好了 SSH 别名：

```bash
ssh mirad-server
```

它对应：

- 主机：`121.41.1.134`
- 用户：`root`
- 私钥：`~/.ssh/id_ed25519_mirad`

服务器当前已经关闭密码登录，仅允许密钥登录。

## 15. 最短可执行版本

如果你只想照着最短步骤来：

### 本机上传

```powershell
scp -r .\backend .\frontend .\deploy .\start.sh .\README.md .\DEPLOY_SERVER.md mirad-server:/opt/mirad-upload/
```

### 服务器执行

```bash
sudo mkdir -p /var/lib/mirad-upload/uploads /var/lib/mirad-upload/users
sudo chown -R www-data:www-data /var/lib/mirad-upload

cd /opt/mirad-upload/frontend
npm install
npm run build

cd /opt/mirad-upload/backend
mvn clean package -DskipTests
sudo chown -R www-data:www-data /opt/mirad-upload/backend/target
sudo chmod -R a+rX /opt/mirad-upload/frontend/dist /opt/mirad-upload/backend/target

sudo cp /opt/mirad-upload/deploy/nginx/mirad-upload.conf /etc/nginx/conf.d/mirad-upload.conf
sudo tee /etc/systemd/system/mirad-upload.service > /dev/null <<'EOF'
[Unit]
Description=Mirad Upload Service
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/mirad-upload/backend
Environment=SERVER_ADDRESS=127.0.0.1
Environment=SERVER_PORT=8081
Environment=MIRAD_DATA_DIR=/var/lib/mirad-upload
Environment=MIRAD_COOKIE_SECURE=false
Environment=MIRAD_SESSION_TIMEOUT=60m
ExecStart=/usr/bin/java -jar /opt/mirad-upload/backend/target/mirad-upload-backend-1.0.0.jar
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/var/lib/mirad-upload /opt/mirad-upload/backend
UMask=027
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable mirad-upload
sudo systemctl restart mirad-upload
sudo nginx -t
sudo systemctl reload nginx
```

### 验证

```bash
curl http://127.0.0.1:8081/api/auth/session
curl http://127.0.0.1:9090/api/auth/session
```

浏览器打开：

- `http://121.41.1.134:9090`
