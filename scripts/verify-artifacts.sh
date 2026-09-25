#!/bin/sh
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

# 로컬 저장소(build/repo)로 아티팩트 및 BOM 일괄 배포
./gradlew publishAllToLocalRepo

# 소비자 프로젝트(examples/artifact-consumer) 테스트 수행
./gradlew -p examples/artifact-consumer test "$@"
