# syntax=docker/dockerfile:1
# ============================================================
# Treatbord 后端镜像（多阶段构建）
#   构建： docker build -f deploy/Dockerfile -t treatbord/app:local .
#   运行： docker compose -f deploy/docker-compose.yml up -d
# 说明：构建上下文必须为**仓库根目录**（.dockerignore 也在仓库根）
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先拷依赖描述与镜像配置，让"依赖下载"与"源码变更"分层缓存
COPY deploy/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -q -s /root/.m2/settings.xml dependency:go-offline || true

# 再拷源码构建（跳过测试：测试需要 MySQL/Redis，由 CI 的 test job 负责）
COPY src ./src
RUN mvn -B -q -s /root/.m2/settings.xml clean package -DskipTests \
 && cp target/*.jar /build/app.jar

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:21-jre

# 时区 + 健康检查所需 curl（并清理 apt 缓存）
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
 && echo "Asia/Shanghai" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

# 非 root 运行（最小权限）
RUN groupadd -r -g 10001 app \
 && useradd -r -u 10001 -g app -d /app -s /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# 就绪探针：依赖 /actuator/health（生产已暴露 health）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
