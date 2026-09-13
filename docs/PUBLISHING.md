# 아티팩트 퍼블리싱 가이드 (Publishing Guide)

이 문서는 `backend-init` 프레임워크 모듈 및 BOM의 퍼블리싱 규칙, 배포 좌표, 로컬 저장소 구성 및 소비자 프로젝트에서의 아티팩트 사용 방법을 설명합니다.

> **안내**: 현재는 로컬 아티팩트 저장소(`build/repo`) 배포 및 검증 체계가 구성되어 있으며, **원격 레지스트리는 다음 단계**에서 연동될 예정입니다.

---

## 1. 퍼블리시 좌표 목록 (12개 아티팩트)

모든 퍼블리시 대상 아티팩트는 그룹 `cc.midolog`와 버전 `0.0.1-SNAPSHOT`을 공통으로 사용하며, `PublishingConventionPlugin`을 통해 kebab-case 명명 규칙(`backend-init-<module-path>`)이 일관되게 적용됩니다.

| No | 아티팩트 ID (artifactId) | 소스 모듈 경로 | 설명 |
|:---|:---|:---|:---|
| 1 | `backend-init-bom` | `platform:bom` | 전체 모듈의 버전을 일괄 제어하는 BOM (Bill of Materials) |
| 2 | `backend-init-domain` | `core:domain` | 순수 Kotlin 도메인 모델 및 포트 인터페이스 |
| 3 | `backend-init-gateway-autoconfigure` | `gateway:autoconfigure` | 게이트웨이 모드 기반 Spring Boot 자동 설정 모듈 |
| 4 | `backend-init-gateway-core` | `gateway:core` | 게이트웨이 필터, 라우팅 및 프록시 핵심 컴포넌트 |
| 5 | `backend-init-gateway-starter` | `gateway:starter` | 게이트웨이 원스톱 탑재용 스타터 라이브러리 |
| 6 | `backend-init-storage-file-local` | `storage:file-local` | 로컬 파일 시스템 저장소 어댑터 및 자동 설정 |
| 7 | `backend-init-storage-jpa` | `storage:jpa` | Spring Data JPA 영속성 어댑터 및 자동 설정 |
| 8 | `backend-init-storage-mybatis` | `storage:mybatis` | MyBatis 영속성 어댑터 및 자동 설정 |
| 9 | `backend-init-support-jwt` | `support:jwt` | JJWT 라이브러리 격리 및 JWT 토큰 코덱 유틸 |
| 10 | `backend-init-support-logging` | `support:logging` | Logback 및 Reactor MDC 로깅 지원 모듈 |
| 11 | `backend-init-support-util` | `support:util` | 순수 Kotlin 공통 유틸리티 |
| 12 | `backend-init-support-web` | `support:web` | WebFlux 필터, 공통 응답 봉투 및 전역 예외 처리 |

> **비퍼블리시 모듈**: `core:application`, `core:batch`, `gateway:app`, `client:storage-file`, `examples:*` 모듈은 배포 대상에서 제외됩니다.

---

## 2. 로컬 저장소 (Local Maven Repository)

퍼블리시 산출물은 루트 프로젝트 하위의 로컬 디렉토리(`build/repo`)에 Maven 레이아웃으로 배포됩니다.

- **기본 경로**: `$rootDir/build/repo`
- **외부 소비자 주입**: 소비자 프로젝트는 `-PbackendInitRepo=/absolute/path/to/repo` 프로퍼티를 전달받아 저장소 경로를 유연하게 재지정할 수 있습니다.

---

## 3. 관련 Gradle 태스크 및 스크립트

### `publishAllToLocalRepo`
루트 프로젝트에 정의된 집계 태스크로, 퍼블리싱 대상인 11개 라이브러리 모듈과 `platform:bom`의 `publishMavenJavaPublicationToLocalRepoRepository` 태스크를 일괄 실행합니다.

```bash
./gradlew publishAllToLocalRepo
```

### `verifyArtifacts`
`publishAllToLocalRepo`를 먼저 실행하여 로컬 저장소에 최신 아티팩트를 반영한 후, 독립 프로젝트인 `examples/artifact-consumer`의 테스트(`test`)를 실행하여 아티팩트 해석과 런타임 영속성 계약을 엔드투엔드로 검증합니다.

```bash
./gradlew verifyArtifacts
```

### `scripts/verify-artifacts.sh`
CI 및 터미널 환경에서 아티팩트 퍼블리시 및 소비자 테스트를 원클릭으로 수행할 수 있는 검증 스크립트입니다.

```bash
./scripts/verify-artifacts.sh
```

---

## 4. 소비자 프로젝트 사용 예시 (`examples/artifact-consumer`)

소비자 프로젝트는 `backend-init` 저장소와 완전히 분리된 독립 Gradle 빌드로 구성할 수 있습니다.

### `settings.gradle`
```groovy
rootProject.name = 'artifact-consumer'
```

### `build.gradle`
BOM 플랫폼을 임포트하면 개별 모듈 선언 시 버전을 생략할 수 있습니다.

```groovy
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.2.21'
    id 'org.jetbrains.kotlin.plugin.spring' version '2.2.21'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

repositories {
    def localRepoDir = project.findProperty('backendInitRepo') ?
        file(project.property('backendInitRepo')) :
        file("$rootDir/../../build/repo")
    maven { url = localRepoDir.toURI() }
    mavenCentral()
    maven { url = 'https://repo.spring.io/milestone' }
}

dependencies {
    // 1. BOM 플랫폼 임포트 (버전 관리 일원화)
    implementation platform("cc.midolog:backend-init-bom:0.0.1-SNAPSHOT")

    // 2. 무버전 아티팩트 의존성 선언
    implementation "cc.midolog:backend-init-domain"
    implementation "cc.midolog:backend-init-support-web"
    runtimeOnly "cc.midolog:backend-init-storage-jpa"

    // 3. 소비자 애플리케이션 의존성
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'com.h2database:h2'
}
```

### `application.yml`
```yaml
storage:
  persistence:
    provider: jpa # 또는 mybatis

spring:
  datasource:
    url: jdbc:h2:mem:consumer_db;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
```
