FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/huicai-backend-*.jar app.jar

EXPOSE 8000

ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8000/api/v1/system/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
