FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY target/huicai-backend-*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
