#!/usr/bin/env bash
# Treatbord 后端启动脚本（加载 .env.local 后 mvn spring-boot:run）
# 用法：
#   ./start-dev.sh           前台启动（Ctrl+C 停止）
#   ./start-dev.sh --restart 先杀掉占用 8080 的进程再启动
#   ./start-dev.sh --bg      后台启动（日志 → app-run.log）
set -e
cd "$(dirname "$0")"

RESTART=0; BACKGROUND=0
for arg in "$@"; do
  case "$arg" in
    --restart) RESTART=1 ;;
    --bg)      BACKGROUND=1 ;;
    *) echo "[start-dev] 未知参数: $arg（可用 --restart / --bg）"; exit 2 ;;
  esac
done

kill_8080() {
  local pid
  pid=$(ss -tlnp 2>/dev/null | grep ":8080 " | grep -oE 'pid=[0-9]+' | cut -d= -f2 | head -1)
  if [ -n "$pid" ]; then
    echo "[start-dev] 停止占用 8080 的进程 pid=$pid"
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 10); do
      ss -tln 2>/dev/null | grep -q ":8080 " || break
      sleep 1
    done
  fi
}

if [ "$RESTART" = "1" ]; then
  kill_8080
fi

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
if [ "$BACKGROUND" = "1" ]; then
  nohup mvn -s settings-mirror.xml spring-boot:run > app-run.log 2>&1 &
  echo "[start-dev] 后台启动中，日志：app-run.log"
  for _ in $(seq 1 40); do
    sleep 2
    if curl -s -m 2 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP; then
      echo "[start-dev] ✅ 已就绪： http://127.0.0.1:8080/actuator/health"
      exit 0
    fi
  done
  echo "[start-dev] ⚠️ 80 秒内未就绪，请查看 app-run.log"; exit 1
fi
mvn -s settings-mirror.xml spring-boot:run
