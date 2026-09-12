## 보고
- **한 것**: `gateway/core`와 `gateway/autoconfigure` 모듈 분리, 조건부 빈 등록 구현, `GatewayModeProperties` 소스 오브 트루스화(validate) 완료. 네 가지 가드 제거 실험 완료 및 복구.
- **안 한 것**: W2/W3 범위인 `gateway/starter`, `gateway/app`, `.env.example`, 문서 갱신, `core/gateway` 디렉터리 삭제 등은 W1 범위 밖이므로 건드리지 않았습니다. `livePostgresTest` 제외, Docker/Postgres 서버 구동 안 함.
- **가드 빼면 빨강(출력)**:
  - (a) embedded proxy 조건 제거: `GatewayAutoConfigurationTest > embedded 모드 빈 구성 가드() FAILED` (rc=1)
  - (b) mode 필수 검증 제거: `GatewayAutoConfigurationTest > gateway mode 미설정시 fail-fast() FAILED` (rc=1)
  - (c) config 금지 import 임시 삽입: `GatewayPackageDependencyTest > config package does not import handler or route() FAILED` (rc=1)
  - (d) WebFlux order 기대값 틀리게 수정: `GatewayAutoConfigurationTest > WebFlux 매핑 순서 실측 가드() FAILED` (rc=1)
- **내린 것**: 기동한 별도 서버/프로세스는 없으며 사용한 Gradle 데몬은 자동 정리됩니다.
- **preflight 출력**:
```
## preflight (refactor/gateway-split-autoconfig @ 48bd8b4, 2026-09-12 12:55)

### 커밋·push 상태
⚠️ 추적 안 되는 파일(커밋 대상이 아니면 무시): ?? PR.md ?? TASK.md ?? exp1.log ?? exp2.log ?? exp_1.log ?? exp_2.log 

### 작업 파일 혼입 (main 대비 추가된 파일)
없음

### git diff origin/main...HEAD --stat
...
 30 files changed, 340 insertions(+), 60 deletions(-)

### ./gradlew build
exit=0

### ./gradlew -p build-logic test
exit=0

### git diff --check
exit=0

✅ preflight 통과 — 이 출력을 PR 본문에 붙이고 보고해라
```
- **기타 증거**: 
  - `git ls-remote --heads origin refactor/gateway-split-autoconfig` 결과: `48bd8b48c0b2d7a3caf49da6f2547e0646fa7061`
