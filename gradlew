#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA="$JAVA_HOME/bin/java"
  [[ -x "$JAVA" ]] || { echo "JAVA_HOME does not point to an executable JDK: $JAVA" >&2; exit 12; }
else
  JAVA="$(command -v java || true)"
  [[ -n "$JAVA" ]] || { echo 'JDK 17 or newer is required' >&2; exit 12; }
fi
JAVA_SPEC="$("$JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)"
JAVA_MAJOR="${JAVA_SPEC#1.}"
if [[ ! "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || ((JAVA_MAJOR<17)); then
  echo "JDK 17+ required (found ${JAVA_SPEC:-unknown}); check JAVA_HOME" >&2
  exit 12
fi
bash "$ROOT/scripts/ensure-gradle-wrapper.sh"
exec "$JAVA" -classpath "$ROOT/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
