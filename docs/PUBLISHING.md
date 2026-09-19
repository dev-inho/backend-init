# 아티팩트 퍼블리싱 가이드 (Publishing Guide)

이 문서는 `backend-init` 프레임워크 모듈 및 BOM의 퍼블리싱 규칙, 배포 좌표, 로컬 및 원격 저장소 구성, 자격 증명 주입, 버전 관리 정책, 그리고 독립 소비자 프로젝트에서의 아티팩트 검증 방법을 설명합니다.

---

## 1. 퍼블리시 아티팩트 좌표 및 동적 구성 원리

모든 퍼블리시 대상 아티팩트는 그룹 `cc.midolog`를 공통으로 사용하며, `PublishingConventionPlugin`을 통해 kebab-case 명명 규칙(`backend-init-<module-path>`)이 일관되게 적용됩니다.

### 1.1 대상 아티팩트 목록 (현재 12개)

| No | 아티팩트 ID (artifactId) | 소스 모듈 경로 | 설명 |
|:---|:---|:---|:---|
| 1 | `backend-init-bom` | `platform:bom` | 전체 프레임워크 모듈의 버전을 일괄 제어하는 BOM (Bill of Materials) |
| 2 | `backend-init-domain` | `core:domain` | 순수 Kotlin 도메인 모델 및 포트 인터페이스 |
| 3 | `backend-init-gateway-autoconfigure` | `gateway:autoconfigure` | 게이트웨이 라우팅 및 필터 Spring Boot 자동 설정 모듈 |
| 4 | `backend-init-gateway-core` | `gateway:core` | 게이트웨이 라우팅, 프록시 및 필터 핵심 컴포넌트 |
| 5 | `backend-init-gateway-starter` | `gateway:starter` | 게이트웨이 원스톱 탑재용 스타터 라이브러리 |
| 6 | `backend-init-storage-file-local` | `storage:file-local` | 로컬 파일 시스템 저장소 어댑터 및 자동 설정 |
| 7 | `backend-init-storage-jpa` | `storage:jpa` | Spring Data JPA 영속성 어댑터 및 자동 설정 |
| 8 | `backend-init-storage-mybatis` | `storage:mybatis` | MyBatis 영속성 어댑터 및 자동 설정 |
| 9 | `backend-init-support-jwt` | `support:jwt` | JJWT 라이브러리 격리 및 JWT 토큰 코덱 유틸 |
| 10 | `backend-init-support-logging` | `support:logging` | Logback 및 Reactor MDC 로깅 지원 모듈 |
| 11 | `backend-init-support-util` | `support:util` | 순수 Kotlin 공통 유틸리티 |
| 12 | `backend-init-support-web` | `support:web` | WebFlux 필터, 공통 응답 봉투 및 전역 예외 처리 |

> **비퍼블리시 모듈**: `core:application`, `core:batch`, `gateway:app`, `client:storage-file`, `examples:*` 모듈은 배포 대상에서 제외됩니다.

### 1.2 동적 BOM 제약 구성 및 검증 원리

- **BOM 제약 동적 추가**: `platform/bom/build.gradle`은 하드코딩된 목록 대신, 루트 프로젝트의 서브프로젝트 중 `cc.midolog.publishing` 플러그인이 적용된 프로젝트를 자동으로 탐색하여 `dependencies.constraints.add('api', sp)`로 의존성 제약에 등록합니다. 따라서 향후 S3 저장소 모듈(`storage:file-s3` 등)이 추가되더라도 BOM 코드를 수정할 필요 없이 자동으로 포함됩니다.
- **`verifyBom` 동적 검증**: `build.gradle`의 `verifyBom` 태스크 역시 하드코딩 목록을 배제하고, 현재 빌드에서 `cc.midolog.publishing` 플러그인이 적용된 모듈의 `mavenJava` publication artifactId 목록을 동적으로 수집하여 실제 생성된 BOM pom.xml의 `dependencyManagement` 항목과 1:1 대조합니다.

---

## 2. 저장소 (Maven Repositories)

`PublishingConventionPlugin`은 로컬 파일 시스템 저장소와 원격 GitHub Packages Maven 레지스트리를 모두 지원합니다.

### 2.1 로컬 저장소 (Local Maven Repository)

퍼블리시 산출물은 기본적으로 루트 프로젝트 하위의 로컬 디렉토리(`build/repo`)에 Maven 레이아웃으로 배포됩니다.

- **기본 경로**: `$rootDir/build/repo`
- **저장소 경로 오버라이드**: `-PbackendInitRepo=/absolute/path/to/repo` 프로퍼티를 통해 유연하게 변경할 수 있습니다.
- **배포 태스크**: `./gradlew publishAllToLocalRepo`

### 2.2 원격 저장소 (GitHub Packages Maven Registry)

GitHub Packages는 소유자(`dev-inho`)와 저장소(`backend-init`)에 바인딩된 Maven 패키지 레지스트리입니다.

- **저장소 URL**: `https://maven.pkg.github.com/dev-inho/backend-init`
- **URL 오버라이드**: `-PbackendInitGithubRepoUrl=https://...` 프로퍼티 또는 `BACKEND_INIT_GITHUB_REPO_URL` 환경 변수를 통해 커스텀 레지스트리로 변경할 수 있습니다.
- **배포 태스크**: `./gradlew publishAllToGithubPackages`

---

## 3. 자격 증명 (Credentials) 주입 및 안전성

### 3.1 자격 증명 주입 경로

원격 GitHub Packages에 접근 및 게시하기 위한 자격 증명은 환경 변수 또는 Gradle 프로퍼티를 통해 전달됩니다:

1. **사용자명 (Username)**:
   - 1순위: Gradle 프로퍼티 `gpr.user`
   - 2순위: 환경 변수 `GITHUB_ACTOR` 또는 `GITHUB_USERNAME`
   - 3순위: Gradle 프로퍼티 `githubUsername`
   - 기본 fallback: `dev-inho` (Personal Access Token에 write 권한이 부여된 경우 저장소 소유자 계정으로 인증 가능)
2. **비밀번호 / 토큰 (Password / Token)**:
   - 1순위: Gradle 프로퍼티 `gpr.key`
   - 2순위: 환경 변수 `GITHUB_TOKEN` 또는 `GH_TOKEN`
   - 3순위: Gradle 프로퍼티 `githubToken`

### 3.2 PM 환경에서의 메모리 주입 경로

실제 원격 게시 및 토큰 조작은 오직 **PM**만 수행합니다. PM은 파일이나 소스 코드에 비밀을 영구 기록하지 않고, 메모리 상에서 환경 변수로 토큰을 주입합니다:

```bash
# PM 전용: gh CLI 토큰을 메모리에서 환경 변수로 주입
export GITHUB_TOKEN=$(gh auth token)
```

### 3.3 비밀 미기록 원칙 및 Fail-Fast 가드

- **비밀 미기록**: 토큰 및 자격 증명 값은 소스 코드, 문서, 빌드 로그, git 커밋, 명령행 인자에 절대 기록되거나 노출되지 않습니다.
- **자격 증명 누락 시 안전한 차단**: 원격 배포 태스크(`publishAllToGithubPackages`, `publishMavenJavaPublicationToGithubRepository`)는 유효한 토큰이 제공되지 않은 경우 즉시 빌드를 중단하며, 명확한 오류 원인을 안내합니다:
  ```
  GitHub Packages credentials missing: GITHUB_TOKEN (or GH_TOKEN / gpr.key) must be provided to publish to GitHub Packages repository.
  ```
- **로컬 배포 격리**: 자격 증명이 없는 환경에서도 로컬 배포(`publishAllToLocalRepo`) 및 로컬 기반 검증(`verifyBom`, `verifyArtifacts`)은 완전히 독립적으로 정상 동작합니다.

> **현재 상태 안내**: 현재 개인 GitHub CLI 토큰은 `read:packages` 권한 부족으로 인한 HTTP 403 상태이며, `gh auth refresh` 기기 인증은 만료(expired_token)되었습니다. 따라서 아래 3.4의 GitHub Actions 경로가 권장됩니다.

### 3.4 GitHub Actions 자동 게시 경로 (인증 장애 대안 및 권장)

현재 개인 자격 증명 권한 부족에 대한 안전하고 공식적인 대안으로, **GitHub Actions 워크플로우(`.github/workflows/publish.yml`)**를 통한 원격 게시 체계가 구성되어 있습니다.

GitHub Actions 환경에서는 워크플로우 레벨에서 `permissions: contents: read, packages: write` 권한을 부여함으로써, 별도의 개인 PAT 발급 없이도 GitHub 내장 `secrets.GITHUB_TOKEN`을 통해 안전하게 `dev-inho/backend-init` Packages 레지스트리에 패키지를 게시하고 소비자 검증을 수행할 수 있습니다.

#### 릴리스 태그 실행 절차 (PM 전용)

1. **사전 로컬 검증**: 최종 검증된 HEAD 커밋에서 로컬 검증 태스크(`build-logic test`, `verifyBom`, `verifyArtifacts`)가 통과되었는지 확인합니다.
2. **릴리스 태그 생성 및 푸시**:
   `main` 브랜치 직접 푸시나 PR 머지 없이, 최종 검증된 HEAD 커밋에 명시적인 `backend-init-v<SemanticVersion>` 태그를 생성하여 푸시합니다:
   ```bash
   # 예: 0.1.0 버전 릴리스 태그 생성 및 푸시 (PM만 수행)
   git tag backend-init-v0.1.0
   git push origin backend-init-v0.1.0
   ```
3. **워크플로우 자동 실행 단계**:
   - `publish.yml` 워크플로우가 태그 푸시를 감지하여 기동합니다.
   - 태그명에서 Semantic Version을 엄격하게 정규식(`^backend-init-v([0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?)$`)으로 검증하여 쉘 주입을 원천 차단하고 버전을 추출합니다.
   - 베이스라인 검증(`build-logic test`, `verifyBom`)을 거쳐 `publishAllToGithubPackages -PbackendInitVersion=<version>`으로 11개 모듈 및 BOM을 원격 레지스트리에 일괄 게시합니다.
   - 게시 직후 독립 소비자 프로젝트(`examples/artifact-consumer`)를 원격 저장소(`https://maven.pkg.github.com/dev-inho/backend-init`) 전용 모드로 실행하여 원격 아티팩트 다운로드, 해석 및 JPA/MyBatis 영속성 계약을 실시간 검증합니다.
4. **후속 모듈 확장성**:
   - 향후 추가될 S3 저장소 모듈(`storage:file-s3` 등)도 `cc.midolog.publishing` 플러그인만 적용하면 별도 워크플로우 수정 없이 자동으로 BOM 제약 및 GitHub Packages 배포 대상에 집계됩니다.

---

## 4. 버전 관리 및 릴리스 비덮어쓰기 정책

### 4.1 버전 결정 정책 (Semantic Versioning)

버전은 `MAJOR.MINOR.PATCH[-SNAPSHOT]` 형식을 따릅니다.
루트 `build.gradle`은 다음 우선순위로 버전을 결정합니다:

1. Gradle 프로퍼티: `-PbackendInitVersion=...` 또는 `-Pversion=...`
2. 환경 변수: `BACKEND_INIT_VERSION=...`
3. 기본값: `0.0.1-SNAPSHOT`

예시:
```bash
# 기본 SNAPSHOT 버전으로 빌드
./gradlew build

# 새 SNAPSHOT 버전 명시
./gradlew build -PbackendInitVersion=0.0.2-SNAPSHOT

# 정식 릴리스 버전 지정
./gradlew build -PbackendInitVersion=0.1.0
```

### 4.2 기존 릴리스 비덮어쓰기 (Non-overwriting Release Policy)

- **릴리스 버전의 불변성**: 릴리스 버전(non-SNAPSHOT)은 한 번 배포되면 절대 수정되거나 덮어쓰여져서는 안 됩니다. GitHub Packages 역시 동일한 릴리스 버전의 덮어쓰기를 허용하지 않고 오류를 반환합니다.
- **릴리스 가드**: 릴리스 버전을 원격에 게시할 때는 새로운 고유 버전을 지정해야 하며, 실수로 기존 릴리스를 덮어쓰려 하는 작업을 방지합니다. 명시적인 강제 재배포 플래그(`-PallowReleaseOverwrite=true`)가 없는 한 릴리스의 불변성이 엄격히 보장됩니다.

---

## 5. 독립 소비자 검증 (`examples/artifact-consumer`)

소비자 프로젝트(`examples/artifact-consumer`)는 `backend-init` 프레임워크를 외부 의존성으로 참조하는 독립 빌드 예제입니다.

### 5.1 로컬 vs 원격 저장소 명시적 분리 및 Fallback 우회 차단

원격 아티팩트 검증 시 로컬 아티팩트(`build/repo`)로의 자동 fallback 우회가 발생하지 않도록 저장소 선택이 엄격히 분리되어 있습니다:

- **원격 검증 모드 (`-PbackendInitRepoUrl=...`)**:
  원격 저장소 URL이 명시되면 **로컬 `build/repo` 저장소는 Gradle 저장소 목록에 등록조차 되지 않습니다**. 따라서 오직 원격 레지스트리로부터 아티팩트를 다운로드하여 검증을 수행하며, 로컬 캐시나 로컬 산출물로 우회되는 현상이 원천 차단됩니다.
- **로컬 검증 모드 (`-PbackendInitRepo=...` 또는 기본값)**:
  원격 레지스트리를 조회하지 않고 지정된 로컬 파일 저장소(`build/repo`)만을 참조합니다.

### 5.2 영속성 어댑터 이중화 검증 (JPA & MyBatis)

소비자 프로젝트는 2가지 영속성 어댑터 모두의 외부 아티팩트 해석 및 동작을 검증합니다:

1. **JPA 영속성 검증 (`ArtifactConsumerJpaPersistenceTest`)**:
   `storage.persistence.provider=jpa` 프로퍼티를 설정하고 H2 인메모리 환경에서 도메인 포트(`SampleRepositoryPort`)를 통한 `Sample` 모델 save/findById 왕복 동작을 검증합니다.
2. **MyBatis 영속성 검증 (`ArtifactConsumerMyBatisPersistenceTest`)**:
   `storage.persistence.provider=mybatis` 프로퍼티를 설정하고 H2 인메모리 환경에서 도메인 포트(`SampleRepositoryPort`)를 통한 `Sample` 모델 save/findById 왕복 동작을 검증합니다.
3. **Fail-Fast 검증 (`ArtifactConsumerFailFastTest`)**:
   `storage.persistence.provider` 프로퍼티가 누락되었을 때 스프링 부트 컨텍스트가 즉시 실패하고 원인 메시지에 핵심 토큰이 포함되는지 검증합니다.

---

## 6. 주요 Gradle 태스크 및 검증 스크립트

### 6.1 Gradle 태스크

| 태스크명 | 그룹 | 설명 |
|:---|:---|:---|
| `publishAllToLocalRepo` | `publishing` | 11개 라이브러리 모듈 및 BOM을 로컬 저장소(`build/repo`)에 일괄 배포 |
| `publishAllToGithubPackages` | `publishing` | 11개 라이브러리 모듈 및 BOM을 GitHub Packages 원격 레지스트리에 일괄 배포 (자격 증명 필수) |
| `verifyBom` | `verification` | 배포된 BOM pom.xml의 dependencyManagement 제약이 모든 publishing 대상 모듈과 일치하는지 동적 검증 |
| `verifyArtifacts` | `verification` | 로컬 배포 수행 후 독립 소비자 프로젝트(`examples/artifact-consumer`)의 JPA/MyBatis 테스트 일괄 수행 |
| `verifyRemoteArtifacts` | `verification` | 원격 GitHub Packages 레지스트리에 게시된 아티팩트를 대상으로 소비자 테스트 수행 (로컬 fallback 차단) |

### 6.2 검증 스크립트

- **로컬 아티팩트 전체 검증**:
  ```bash
  ./scripts/verify-artifacts.sh
  ```
  로컬 저장소로 아티팩트를 배포하고 소비자 테스트를 일괄 실행합니다.

- **원격 아티팩트 소비자 검증 (PM 전용)**:
  ```bash
  # 1. PM 환경에서 토큰 주입
  export GITHUB_TOKEN=$(gh auth token)

  # 2. 원격 아티팩트 소비자 검증 실행 (대상 버전 지정 가능)
  ./scripts/verify-remote-artifacts.sh 0.0.1-SNAPSHOT
  ```
  자격 증명이 설정되지 않은 경우 안전하게 차단되며, 원격 레지스트리만을 명시적으로 참조하여 검증합니다.
