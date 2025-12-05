#!/bin/bash

# 启用错误检查，遇到错误立即退出
set -euo pipefail

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR" || { echo "无法进入脚本目录 $SCRIPT_DIR"; exit 1; }

# 清理并重新编译
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 编译Java代码..."
rm -rf out
mkdir -p out
find src -name "*.java" > sources.txt || { echo "未找到Java源文件"; exit 1; }
javac -d out -encoding UTF-8 @sources.txt || { echo "Java编译失败"; exit 1; }
rm -f sources.txt

# 复制web资源（如果web目录存在）
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 复制Web资源..."
mkdir -p out/web
if [ -d "web" ]; then
  cp -r web/* out/web/ || { echo "Web资源复制失败"; exit 1; }
else
  echo "警告：未找到web目录，跳过资源复制"
fi

# 创建必要目录
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 创建必要目录..."
mkdir -p out/uploads out/users

# 设置JVM参数
JVM_OPTS="-Xms256m -Xmx1024m -Dfile.encoding=UTF-8"

# 启动服务器（检查主类是否存在）
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 启动服务器 (端口: 9090, 内存: $JVM_OPTS)..."
if [ -f "out/com/example/WebServer.class" ]; then
  java $JVM_OPTS -cp out com.example.WebServer
else
  echo "错误：未找到主类 com.example.WebServer.class"
  exit 1
fi