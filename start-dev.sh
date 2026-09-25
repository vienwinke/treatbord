#!/usr/bin/env bash
# Treatbord 后端启动脚本（加载 .env.local 后 mvn spring-boot:run）
# 在你的 WSL 终端里运行：  cd ~/Project/treatbord && ./start-dev.sh
set -e
cd "$(dirname "$0")"

# 加载本地环境变量（含 MySQL 迁移/应用账号密码）
if [ -f .env.local ]; then
  set -a; . ./.env.local; set +a
  echo "[start-dev] 已加载 .env.local"
else
  echo "[start-dev] 警告：未找到 .env.local，Flyway/数据源可能连不上"
fi

echo "[start-dev] 启动后端（8080）..."
mvn -s settings-mirror.xml spring-boot:run
