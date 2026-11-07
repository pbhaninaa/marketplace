# Elastic Beanstalk Docker platform — runtime only (pre-built JAR, copied in as app.jar).
# Do NOT ship the multi-stage backend/Dockerfile to small EB instances; the Maven build times out.
# Bundle with: scripts/aws/package-eb-docker.sh (or .ps1 on Windows).
FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV PORT=8080

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd --system app \
  && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --chown=app:app app.jar /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=8 \
  CMD curl -fsS http://localhost:${PORT:-8080}/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
