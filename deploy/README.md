# 部署说明（Docker 路线）

## 一、文件作用

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 多阶段构建后端镜像（maven 构建 → JRE 21 运行，非 root + HEALTHCHECK） |
| `../.dockerignore` | 构建上下文排除（`.git/docs/miniprogram/target/.env` 等），必须位于**仓库根** |
| `docker-compose.yml` | 编排 nginx + app + mysql + redis（+ `--profile monitoring` 启用 prometheus） |
| `maven-settings.xml` | 构建期 Maven 镜像加速（阿里云公共仓库） |
| `nginx/treatbord.conf.template` | HTTPS 终结、反代、**指标/健康端点限内网**、`/files` 走应用保留签名校验 |
| `nginx/proxy_headers.conf` | 公共代理头（含 X-Forwarded-For，配合应用侧 `TRUSTED_PROXIES` 才被信任） |
| `scripts/backup.sh` | 每日备份：容器内 mysqldump → gzip → 完整性校验 → 保留 N 天 |
| `prometheus/prometheus.yml` | 抓取 `app:8080/actuator/prometheus` |
| `prometheus/alerts.yml` | 告警规则（应用不可达 / 5xx / 慢接口 / 限流激增 / 定时任务失败 / 堆内存 / 连接池排队） |
| `.env.example` | 生产环境变量模板（复制为 `.env`） |

## 二、首次部署

```bash
# 1) 准备环境变量
cp deploy/.env.example deploy/.env
vi deploy/.env            # 填域名、密钥（openssl rand -base64 48）、微信凭证

# 2) 申请证书（首次用 HTTP-01，需 80 端口已放通且域名已解析）
docker run --rm -v "$PWD/deploy/certs:/etc/letsencrypt" \
  -v "$PWD/deploy/certbot-www:/var/www/certbot" \
  certbot/certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN"

# 3) 校验并启动
docker compose -f deploy/docker-compose.yml --env-file deploy/.env config -q
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
docker compose -f deploy/docker-compose.yml ps
```

> `app` **不发布端口**，外部只能经 nginx（HTTPS）；Flyway 会在 app 启动时自动执行 V1~V8 迁移。

## 三、验证

```bash
curl -fsS https://$DOMAIN/actuator/health          # 期望 {"status":"UP"}（仅内网可访问）
curl -s  https://$DOMAIN/api/tasks?page=1          # 公开接口
docker compose -f deploy/docker-compose.yml logs -f app | grep -E 'Started|Migrating'
curl -s http://127.0.0.1:9090/api/v1/targets       # 启用 monitoring 后：target 应为 UP
```

## 四、日常运维

```bash
# 备份（建议加 crontab：0 3 * * * cd /opt/treatbord/deploy && ./scripts/backup.sh >> /var/log/treatbord-backup.log 2>&1）
cd deploy && ./scripts/backup.sh

# 恢复（已验证流程：先建库再导入）
gunzip -c backups/treatbord_YYYYMMDD_HHMMSS.sql.gz | \
  docker compose exec -T mysql mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB"

# 升级（重新构建并滚动重启）
git pull && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build app

# 日志
docker compose -f deploy/docker-compose.yml logs -f --tail=200 app
```

## 五、注意事项

1. **指标端点**：`/actuator/prometheus` 已由 nginx 限制为内网；若直接暴露 app 端口会绕过该限制（因此 compose 不发布 app 端口）。
2. **限流准确性**：反向代理后必须设置 `TRUSTED_PROXIES`（默认 `172.16.0.0/12`，即 Docker 网段），否则应用只看到 nginx 的 IP，限流会把所有用户当成同一来源。
3. **`prom/prometheus:latest`**：当前环境无法访问 Docker Hub API 校验 tag，首次拉取成功后建议固定为具体版本。
4. **证书续期**：certbot 证书 90 天到期，需另配续期定时任务并 reload nginx（部署时按需补）。
