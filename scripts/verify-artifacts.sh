#!/bin/sh
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
./gradlew publishAllToLocalRepo
./gradlew -p examples/artifact-consumer test "$@"
