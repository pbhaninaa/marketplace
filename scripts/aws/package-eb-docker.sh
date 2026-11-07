#!/usr/bin/env bash
# Test + build the MarketPlace API JAR and zip a Docker bundle for Elastic Beanstalk
# (platform: Docker running on 64bit Amazon Linux 2023).
# Output: backend/eb-docker-deploy.zip (gitignored). Usage: scripts/aws/package-eb-docker.sh [out.zip]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$REPO_ROOT/backend"
OUT_ZIP="${1:-$BACKEND/eb-docker-deploy.zip}"
case "$OUT_ZIP" in /*) ;; *) OUT_ZIP="$PWD/$OUT_ZIP" ;; esac

[ -f "$BACKEND/pom.xml" ] || { echo "Backend not found at $BACKEND" >&2; exit 1; }
cd "$BACKEND"

echo "Running tests and packaging in $BACKEND ..."
mvn -B package

JAR="$(ls target/marketplace-api-*.jar 2>/dev/null | grep -v '\.original$' | head -n 1 || true)"
[ -n "$JAR" ] || { echo "target/marketplace-api-*.jar not found" >&2; exit 1; }

STAGE="$BACKEND/eb-docker-stage"
rm -rf "$STAGE"
mkdir -p "$STAGE/.ebextensions"
cp "$JAR" "$STAGE/app.jar"
cp eb/Dockerfile "$STAGE/Dockerfile"
cp eb/Dockerrun.aws.json "$STAGE/Dockerrun.aws.json"
cp eb/.ebextensions/*.config "$STAGE/.ebextensions/"

rm -f "$OUT_ZIP"
(cd "$STAGE" && zip -qr "$OUT_ZIP" .)
rm -rf "$STAGE"

echo "Created: $OUT_ZIP"
echo "Upload in Elastic Beanstalk -> Upload and deploy (platform: Docker on Amazon Linux 2023, t3.small or larger)."
