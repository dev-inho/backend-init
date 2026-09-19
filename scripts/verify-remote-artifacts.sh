#!/bin/sh
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

REMOTE_REPO_URL="${BACKEND_INIT_REPO_URL:-https://maven.pkg.github.com/dev-inho/backend-init}"
VERSION="${1:-0.0.1-SNAPSHOT}"

# 자격 증명 확인 (토큰 내용은 절대 로그나 화면에 노출하지 않음)
TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"
if [ -z "$TOKEN" ]; then
    echo "======================================================================" >&2
    echo "[오류] GitHub Packages 원격 소비자 검증을 위한 자격 증명이 설정되지 않았습니다." >&2
    echo "PM 실행 환경에서 메모리 기반으로 토큰을 주입해 주세요:" >&2
    echo "  export GITHUB_TOKEN=\$(gh auth token)" >&2
    echo "또는 환경 변수 GITHUB_TOKEN 또는 GH_TOKEN을 설정한 뒤 재실행하세요." >&2
    echo "======================================================================" >&2
    exit 1
fi

echo "======================================================================"
echo "[검증] GitHub Packages 원격 레지스트리 기반 소비자 검증을 시작합니다."
echo "  - 원격 저장소 URL: $REMOTE_REPO_URL"
echo "  - 대상 버전: $VERSION"
echo "  - 로컬 fallback 차단: backendInitRepoUrl 명시 모드 활성화"
echo "======================================================================"

# 로컬 build/repo를 참조하지 않고 원격 저장소만 참조하도록 -PbackendInitRepoUrl을 전달합니다.
./gradlew -p examples/artifact-consumer test \
    "-PbackendInitRepoUrl=$REMOTE_REPO_URL" \
    "-PbackendInitVersion=$VERSION" \
    "${@:2}"
