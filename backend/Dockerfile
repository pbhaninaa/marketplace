FROM maven:3.9.14-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package \
  && mv target/marketplace-api-*.jar /app/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd --system app \
  && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build /app/app.jar /app/app.jar
RUN chown app:app /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=8 \
  CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health/liveness | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
