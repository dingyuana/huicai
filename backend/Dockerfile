FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/huicai-backend-*.jar app.jar

# SPEC-P152：logback 文件日志（LOG_PATH=/app/logs）。compose 把宿主 ./logs/backend
# 挂进来时属主是宿主用户，容器内显式收权避免权限歧义。
RUN mkdir -p /app/logs

EXPOSE 8000

ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8000/api/v1/system/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
