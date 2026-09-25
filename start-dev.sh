#!/usr/bin/env bash
# Treatbord 后端启动脚本（加载 .env.local 后 mvn spring-boot:run）
# 用法：  cd ~/Project/treatbord && ./start-dev.sh
set -e
cd "$(dirname "$0")"

# ---------- 1. 端口占用检查（避免"启动成功但进程退出"的困惑）----------
if command -v ss >/dev/null 2>&1 && ss -tlnp 2>/dev/null | grep -q ":8080 "; then
  echo "[start-dev] ⚠️  端口 8080 已被占用——后端可能已在运行："
  ss -tlnp 2>/dev/null | grep ":8080 " | sed 's/^/    /'
  echo
  echo "[start-dev] 若需重启，请先停止占用进程："
  echo "    PID=\$(ss -tlnp | grep 8080 | grep -oE 'pid=[0-9]+' | cut -d= -f2 | head -1); kill \$PID"
  echo "  或直接访问现有实例： http://127.0.0.1:8080/actuator/health"
  exit 1
fi

# ---------- 2. 依赖服务检查（MySQL / Redis）----------
for port in 3306 6379; do
  if ! ss -tlnp 2>/dev/null | grep -q ":$port "; then
    echo "[start-dev] ⚠️  端口 $port 未监听（MySQL=3306 / Redis=6379）"
    echo "    启动依赖： /mnt/c/Windows/System32/wsl.exe -u root -e bash -c 'systemctl start mysql redis-server'"
  fi
done

# ---------- 3. 加载环境变量 ----------
if [ -f .env.local ]; then
  set -a; . ./.env.local; set +a
  echo "[start-dev] 已加载 .env.local"
else
  echo "[start-dev] ⚠️  未找到 .env.local，数据源/Flyway 可能连不上"
  echo "    参考 .env.example 创建： cp .env.example .env.local"
  exit 1
fi

echo "[start-dev] 启动后端（8080，profile=${SPRING_PROFILES_ACTIVE:-dev}）..."
mvn -s settings-mirror.xml spring-boot:run
