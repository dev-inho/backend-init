#!/bin/sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== 1. 기본 bootJar 빌드 (고객 모듈 없음) ==="
./gradlew :core:application:bootJar -x test -q
DEFAULT_JAR="$(ls core/application/build/libs/*.jar | grep -v 'plain' | head -1)"
echo "기본 bootJar: $DEFAULT_JAR"
DEFAULT_ACME_COUNT=$(unzip -l "$DEFAULT_JAR" | grep -c "BOOT-INF/lib/acme" || true)
echo "기본 bootJar 내 acme jar 건수: $DEFAULT_ACME_COUNT"

echo "=== 2. -Pcustomer=acme bootJar 빌드 ==="
./gradlew :core:application:bootJar -Pcustomer=acme -x test -q
ACME_JAR="$(ls core/application/build/libs/*.jar | grep -v 'plain' | head -1)"
echo "Acme bootJar: $ACME_JAR"
ACME_COUNT=$(unzip -l "$ACME_JAR" | grep -c "BOOT-INF/lib/acme" || true)
echo "Acme bootJar 내 acme jar 건수: $ACME_COUNT"

if [ "$DEFAULT_ACME_COUNT" -eq 0 ] && [ "$ACME_COUNT" -gt 0 ]; then
    echo "✅ 검증 성공: 기본 빌드에는 acme jar가 없고, -Pcustomer=acme 빌드에만 포함됨 ($ACME_COUNT 건)"
    exit 0
else
    echo "🔴 검증 실패: 기본 빌드 acme=$DEFAULT_ACME_COUNT, acme 빌드 acme=$ACME_COUNT"
    exit 1
fi
