#!/usr/bin/env bash
# Runs the real Spring Boot app against the throwaway Postgres container
# Playwright itself owns as a separate `webServer` entry (playwright.config.js)
# — port 5433, distinct from the real dev DB on 5432, so Phase B E2E runs
# never touch real local data.
set -euo pipefail

DB_PORT="${DB_PORT:-5433}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

until (exec 3<>"/dev/tcp/localhost/${DB_PORT}") 2>/dev/null; do
  sleep 0.5
done
exec 3<&- 3>&-
echo "[start-backend] Postgres reachable on port ${DB_PORT}."

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
