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

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run --rm --name "$CONTAINER_NAME" -p "${DB_PORT}:5432" \
  -e POSTGRES_DB=store_manager -e POSTGRES_USER=store_manager -e POSTGRES_PASSWORD=store_manager \
  -d postgres:16-alpine >/dev/null

until docker exec "$CONTAINER_NAME" pg_isready -U store_manager >/dev/null 2>&1; do
  sleep 0.5
done

# Run directly against infrastructure/pom.xml (not `-pl infrastructure -am`
# from the root): the `application` module dependency resolves from the
# local repo (already `mvn install`ed by prior sessions), and running from
# infrastructure's own pom lets Maven resolve the `spring-boot` plugin
# prefix against the version it actually pins (spring-boot.version=3.3.4)
# instead of either failing prefix resolution entirely or, with a bare
# fully-qualified `org.springframework.boot:spring-boot-maven-plugin:run`
# from the reactor root, silently resolving the latest plugin release
# (a 4.x milestone) against the parent aggregator pom, which has no main
# class.
DB_PORT="$DB_PORT" mvn -f "$REPO_ROOT/infrastructure/pom.xml" spring-boot:run
