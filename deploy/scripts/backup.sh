#!/usr/bin/env bash
# ============================================================
# Treatbord 数据库备份（每日）
#   - 在 mysql 容器内执行 mysqldump（主机无需安装客户端，密钥不出容器命令行）
#   - gzip 压缩 + 完整性校验 + 保留 N 天
# 用法：
#   cd deploy && ./scripts/backup.sh
# 定时（crontab -e）：
#   0 3 * * * cd /opt/treatbord/deploy && ./scripts/backup.sh >> /var/log/treatbord-backup.log 2>&1
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."            # 切到 deploy/，保证 compose 能找到 .env

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
TS="$(date +%Y%m%d_%H%M%S)"
OUT="${BACKUP_DIR}/treatbord_${TS}.sql.gz"

mkdir -p "$BACKUP_DIR"

log() { echo "[$(date '+%F %T')] $*"; }

log "开始备份 → ${OUT}"
# -T 禁用 TTY；容器内的 MYSQL_USER/MYSQL_PASSWORD/MYSQL_DATABASE 由 compose 注入
if docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
     'mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF \
        -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
     | gzip -9 > "$OUT"; then
  log "导出完成，大小 $(du -h "$OUT" | cut -f1)"
else
  log "❌ 导出失败"; rm -f "$OUT"; exit 1
fi

# 完整性校验：gzip 可解 + 至少 14 张表（业务表数量）
if ! gzip -t "$OUT" 2>/dev/null; then
  log "❌ gzip 校验失败"; exit 1
fi
TABLES=$(gunzip -c "$OUT" | grep -c 'CREATE TABLE' || true)
log "校验通过：CREATE TABLE 语句 ${TABLES} 条"
if [ "$TABLES" -lt 14 ]; then
  log "❌ 表数量异常（期望 ≥14），保留文件以便排查：${OUT}"; exit 1
fi

# 清理过期备份
DELETED=$(find "$BACKUP_DIR" -name 'treatbord_*.sql.gz' -type f -mtime "+${KEEP_DAYS}" -print -delete | wc -l)
log "清理 ${DELETED} 个超过 ${KEEP_DAYS} 天的旧备份"
log "现有备份：$(ls -1 "$BACKUP_DIR"/treatbord_*.sql.gz 2>/dev/null | wc -l) 个，共 $(du -sh "$BACKUP_DIR" | cut -f1)"
log "✅ 备份完成"
