# Storage Starter 가이드 (Spring Boot Starter & 확장 계약)

`backend-init`의 영속성 계층(`storage:jpa`, `storage:mybatis`)은 호스트 애플리케이션의 컴포넌트 스캔에 의존하지 않고, 스프링 부트 표준 **AutoConfiguration** 메커니즘을 통해 동작하는 독립적인 **Spring Boot Starter** 형태로 제공됩니다.

외부 소비자 애플리케이션은 의존성을 추가하고 필수 provider 프로퍼티를 선언하는 것만으로 도메인 저장소 포트(`SampleRepositoryPort`, `UserRepositoryPort`, `FileMetaRepositoryPort`)의 기본 어댑터를 자동 주입받을 수 있습니다.

---

## 1. 의존성 설정 (Gradle)

소비자 애플리케이션의 `build.gradle`에 필요한 도메인 및 스토리지 스타터를 선언합니다:

```groovy
dependencies {
    // 1. 순수 도메인 모델 및 포트 인터페이스 (컴파일 타임 참조)
    implementation project(':core:domain')

    // 2. 영속성 기술 스타터 (런타임 주입: 코드는 도메인 포트만 보고 컴파일되므로 runtimeOnly 사용)
    runtimeOnly project(':storage:jpa')
    // 또는
    // runtimeOnly project(':storage:mybatis')
}
```

> **단일 provider 선택**: 런타임 클래스패스에 두 모듈이 모두 존재할 수도 있으나, 실제 활성화는 `storage.persistence.provider` 프로퍼티를 통해 단 하나만 선택됩니다.

---

## 2. Provider 프로퍼티 설정 및 Fail-Fast

### 프로퍼티 표

| 설정 키 | 환경 변수 대응 | 허용 값 | 기본값 | 설명 |
|---|---|---|---|---|
| `storage.persistence.provider` | `STORAGE_PERSISTENCE_PROVIDER` | `jpa`, `mybatis` | **없음 (필수)** | 활성화할 영속성 공급자 선택 |

### Fail-Fast 원칙
소비자 애플리케이션 구동 시 `storage.persistence.provider` 프로퍼티가 누락되었거나 오타가 있는 경우, 빈 팩토리 후처리 단계(`BeanFactoryPostProcessor`)에서 일반 싱글톤 빈이 만들어지기 전에 즉시 실패(`fail-fast`)합니다:

```
java.lang.IllegalStateException: storage.persistence.provider 값이 설정되지 않았습니다. jpa 또는 mybatis를 지정하세요.
```
또는
```
java.lang.IllegalStateException: 알 수 없는 persistence provider입니다: foo. 지원되는 값: jpa, mybatis
```

이를 통해 잘못된 설정으로 인한 런타임 NullPointerException이나 의존성 미주입 사고를 조기에 원천 차단합니다.

---

## 3. MyBatis 환경 후처리기 (EnvironmentPostProcessor)

`storage:mybatis` 스타터는 `MyBatisDefaultPropertiesEnvironmentPostProcessor`를 내장하여, 소비자가 별도 설정을 작성하지 않아도 아래 기본 설정을 환경 최하위 우선순위(`addLast`)로 자동 주입합니다:

1. `mybatis.mapper-locations`: `classpath*:mapper/**/*.xml`
2. `mybatis.configuration.map-underscore-to-camel-case`: `true`

### 소비자 명시값 우선 원칙
`EnvironmentPostProcessor`의 프로퍼티 소스는 최하위 순위(`addLast`)로 등록되므로, 소비자 애플리케이션이 `application.yml`이나 환경 변수에서 MyBatis 속성을 명시적으로 정의할 경우, **소비자가 지정한 설정이 기본값을 완벽히 덮어씁니다**. 기본 매퍼 위치(`classpath*:mapper/**/*.xml`)는 스타터 내부에서 완결되므로 소비자가 별도로 지정할 필요가 없습니다.

예시 (소비자 추가 설정 지정):
```yaml
mybatis:
  configuration:
    default-fetch-size: 100
```

---

## 4. 소비자 빈 오버라이드 (@ConditionalOnMissingBean)

스타터가 제공하는 모든 포트 어댑터 빈은 `@ConditionalOnMissingBean`으로 등록됩니다:

- `SampleRepositoryPort`
- `UserRepositoryPort`
- `FileMetaRepositoryPort`

### 확장 및 교체 계약
소비자 애플리케이션이 특정 포트에 대해 자체 구현체를 스프링 빈(`@Bean` 또는 `@Component`)으로 등록하면, Spring Boot의 기본 `allow-bean-definition-overriding=false` 상태에서도 스타터의 기본 자동 구성 어댑터가 조용히 물러납니다(`back off`).

```kotlin
@Configuration
class CustomRepositoryConfiguration {
    @Bean
    fun sampleRepositoryPort(): SampleRepositoryPort {
        return CustomSampleRepository()
    }
}
```

이때 프레임워크의 구체 어댑터 클래스(JPA/MyBatis)를 알 필요 없이, 순수 도메인 포트 인터페이스만 구현하여 빈으로 노출하면 됩니다.

---

## 5. JPA vs MyBatis 선택 기준

| 기준 | JPA (`storage:jpa`) | MyBatis (`storage:mybatis`) |
|---|---|---|
| **적합한 유즈케이스** | 일반적인 OLTP 비즈니스 CRUD, 도메인 주도 설계, 타입 안전한 쿼리 | 복잡한 대규모 통계/집계 SQL, 정밀한 인덱스 힌트 및 DB 종속 튜닝이 필요한 레거시 연동 |
| **타입 안전성** | QueryDSL 7.6 Q-Type 및 Repository를 통한 컴파일 타임 검증 | XML 및 맵 기반 매핑 (런타임 바인딩 검증 필요) |
| **트랜잭션 모델** | `TransactionOperations` 및 Spring Data 트랜잭션 관리 | Spring MyBatis SqlSessionTemplate 기반 트랜잭션 관리 |
| **도메인 순수성** | Gradle DSL(`jpaDsl`)을 통한 엔티티 생성으로 순수 Kotlin 도메인 모델 유지 | 도메인 모델 어노테이션 비의존 |

---

## 6. 최소 소비자 레퍼런스 앱

저장소 스타터의 자동 구성, fail-fast, 소비자 오버라이드, 두 provider 실증의 실제 구현 코드는 아래 모듈에서 확인할 수 있습니다:

- [examples/minimal-app](../examples/minimal-app): 호스트 컴포넌트 스캔에 기대지 않는 독립 패키지(`cc.midolog.examples.minimal`) 기반의 최소 소비자 Spring Boot 애플리케이션
  - [MinimalApplication.kt](../examples/minimal-app/src/main/kotlin/cc/midolog/examples/minimal/MinimalApplication.kt): 베이스 패키지 격리 구동
  - [MinimalSampleController.kt](../examples/minimal-app/src/main/kotlin/cc/midolog/examples/minimal/controller/MinimalSampleController.kt): 도메인 포트만 주입받는 웹 엔드포인트
  - [MinimalAppJpaPersistenceTest.kt](../examples/minimal-app/src/test/kotlin/cc/midolog/examples/minimal/MinimalAppJpaPersistenceTest.kt): JPA H2 왕복 검증
  - [MinimalAppMyBatisPersistenceTest.kt](../examples/minimal-app/src/test/kotlin/cc/midolog/examples/minimal/MinimalAppMyBatisPersistenceTest.kt): MyBatis H2 왕복 검증
  - [MinimalAppFailFastTest.kt](../examples/minimal-app/src/test/kotlin/cc/midolog/examples/minimal/MinimalAppFailFastTest.kt): provider 미설정 시 fail-fast 실증
  - [MinimalAppConsumerOverrideTest.kt](../examples/minimal-app/src/test/kotlin/cc/midolog/examples/minimal/MinimalAppConsumerOverrideTest.kt): 사용자 빈 정의 시 자동 구성 양보 실증
