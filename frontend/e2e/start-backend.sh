#!/usr/bin/env bash
# Boots a throwaway PostgreSQL container (distinct port from the real dev DB
# on 5432, so Phase B E2E runs never touch real local data) and then runs the
# real Spring Boot app against it. Invoked as one Playwright `webServer`
# command (design.md: "a docker-compose or npm script that boots both").
set -euo pipefail

CONTAINER_NAME="store-manager-e2e-db"
DB_PORT="${DB_PORT:-5433}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cleanup() {
  docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

echo "[start-backend] starting throwaway Postgres on port ${DB_PORT}..."
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run --rm --name "$CONTAINER_NAME" -p "${DB_PORT}:5432" \
  -e POSTGRES_DB=store_manager -e POSTGRES_USER=store_manager -e POSTGRES_PASSWORD=store_manager \
  -d postgres:16-alpine >/dev/null

until docker exec "$CONTAINER_NAME" pg_isready -U store_manager >/dev/null 2>&1; do
  sleep 0.5
done
echo "[start-backend] Postgres ready."

# Install `application` into the local repo first: on a fresh checkout (CI)
# it isn't there yet, and running `spring-boot:run` directly against
# infrastructure/pom.xml can't resolve it otherwise. Cheap/no-op locally
# once it's already installed.
echo "[start-backend] installing application module..."
mvn -f "$REPO_ROOT/pom.xml" -pl application -am install -DskipTests -q

# Run directly against infrastructure/pom.xml (not `-pl infrastructure -am`
# from the root): running from infrastructure's own pom lets Maven resolve
# the `spring-boot` plugin prefix against the version it actually pins
# (spring-boot.version=3.3.4) instead of either failing prefix resolution
# entirely or, with a bare fully-qualified
# `org.springframework.boot:spring-boot-maven-plugin:run` from the reactor
# root, silently resolving the latest plugin release (a 4.x milestone)
# against the parent aggregator pom, which has no main class.
echo "[start-backend] starting Spring Boot app..."
DB_PORT="$DB_PORT" mvn -f "$REPO_ROOT/infrastructure/pom.xml" spring-boot:run
