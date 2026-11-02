#!/usr/bin/env bash
# 병원·병동 기능 로컬 통합 검증: 로컬 OIDC 발급자 + 백엔드 jar(임시 H2 파일 DB) + Android 네트워크 계층 JVM 테스트.
# 실제 Kakao 로그인·Android 기기 화면은 검증하지 않는다. 합성 subject만 사용한다.
# 필요: node, JDK 21, Android SDK(ANDROID_HOME). 사용: scripts/local-e2e/run-hospital-ward-e2e.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
ISSUER_PORT=18090
API_PORT=18080
ADMIN_SUBJECT="e2e-hospital-admin"
cleanup() { kill "${API_PID:-}" "${ISSUER_PID:-}" 2>/dev/null || true; rm -rf "$WORK"; }
trap cleanup EXIT

PORT=$ISSUER_PORT node "$ROOT/scripts/local-e2e/mock-oidc-issuer.mjs" > "$WORK/issuer.log" 2>&1 &
ISSUER_PID=$!

(cd "$ROOT/backend" && ./gradlew -q bootJar)
JAR="$(ls "$ROOT"/backend/build/libs/*.jar | grep -v plain | head -1)"
DB_PATH="$WORK/nurseflow"
command -v cygpath >/dev/null && DB_PATH="$(cygpath -m "$DB_PATH")"
NURSEFLOW_OIDC_ISSUER_URI="http://127.0.0.1:$ISSUER_PORT" \
NURSEFLOW_OIDC_AUDIENCE=nurseflow-api \
NURSEFLOW_BOOTSTRAP_HOSPITAL_NAME="E2E 가상 병원" \
NURSEFLOW_BOOTSTRAP_HOSPITAL_ADMIN_SUBJECT="$ADMIN_SUBJECT" \
SPRING_DATASOURCE_URL="jdbc:h2:file:$DB_PATH;DB_CLOSE_ON_EXIT=FALSE" \
SERVER_PORT=$API_PORT \
java -jar "$JAR" > "$WORK/api.log" 2>&1 &
API_PID=$!

for _ in $(seq 1 90); do
  grep -q "Started NurseFlowBackendApplication" "$WORK/api.log" && break
  grep -q "Application run failed" "$WORK/api.log" && { tail -40 "$WORK/api.log"; exit 1; }
  sleep 1
done
grep -q "Started NurseFlowBackendApplication" "$WORK/api.log" || { echo "서버 기동 시간 초과"; exit 1; }

cd "$ROOT/android"
NURSEFLOW_LIVE_BASE_URL="http://127.0.0.1:$API_PORT" \
NURSEFLOW_LIVE_ISSUER="http://127.0.0.1:$ISSUER_PORT" \
NURSEFLOW_LIVE_HOSPITAL_ADMIN="$ADMIN_SUBJECT" \
./gradlew :app:testDebugUnitTest --tests '*LiveOrganizationApiTest*' --rerun-tasks --console=plain
