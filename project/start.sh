#!/bin/bash

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# 清理并重新编译
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 编译Java代码..."
rm -rf out
mkdir -p out
find src -name "*.java" > sources.txt
javac -d out -encoding UTF-8 @sources.txt
rm sources.txt

# 复制web资源
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 复制Web资源..."
mkdir -p out/web
cp -r web/* out/web/

# 创建上传目录
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 创建上传目录..."
mkdir -p out/uploads

# 设置JVM参数增加内存
JVM_OPTS="-Xms256m -Xmx1024m -Dfile.encoding=UTF-8"

# 启动服务器
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 启动服务器 (端口: 9090, 内存: $JVM_OPTS)..."
java $JVM_OPTS -cp out com.example.WebServer
