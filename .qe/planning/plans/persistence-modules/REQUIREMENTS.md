# Requirements — persistence-modules

> backend-init의 persistence 기술 선택지를 브랜치가 아니라 모듈로 분리한다.
> 원칙: `core:domain`은 프레임워크 비의존 Plain Kotlin/Java 모델과 포트만 가진다.

## 배경
- 현재 저장소는 헥사고날 멀티모듈 구조를 사용하며 `core:domain`에 도메인 모델과 repository port가 있다.
- `storage:mybatis`는 이미 adapter 모듈로 존재한다.
- JPA/Hibernate를 도입하려면 `@Entity`, Spring Data repository, transaction 설정이 필요하지만 이를 domain에 넣으면 도메인 순수성이 깨진다.
- WebFlux + coroutine 스택에서 JPA는 blocking persistence이므로 런타임 경계와 dispatcher/transaction 정책을 명시해야 한다.

## Functional Requirements

| ID | 우선순위 | 요구사항 |
|----|----------|----------|
| FR-01 | P0 | `core:domain`은 JPA/MyBatis/Spring annotation 없이 모델과 port만 유지한다. |
| FR-02 | P0 | 기존 `storage:mybatis`를 persistence adapter로 정리하고 profile/조건부 빈으로 선택 가능하게 만든다. |
| FR-03 | P0 | 신규 `storage:jpa` 모듈을 추가하고 JPA entity/repository/adapter/mapper를 domain 밖에 둔다. |
| FR-04 | P0 | `core:application`은 `SampleRepositoryPort`만 의존하고 구체 persistence 구현에 compile-time 의존하지 않는다. |
| FR-05 | P1 | `mybatis`, `jpa` profile별 datasource/JPA/MyBatis 설정을 분리한다. |
| FR-06 | P1 | 두 adapter가 동일한 port contract를 만족하는지 공통 contract test 또는 동등 단위 테스트를 둔다. |
| FR-07 | P2 | README/docs에 persistence 선택 구조와 실행 방법을 문서화한다. |

## Non-Functional Requirements

| ID | 우선순위 | 요구사항 |
|----|----------|----------|
| NFR-01 | P0 | 의존 방향은 `core:application -> core:domain`, `storage:* -> core:domain`이며 domain은 storage를 모른다. |
| NFR-02 | P0 | JPA blocking I/O는 WebFlux event loop를 막지 않도록 adapter 경계를 명확히 한다. |
| NFR-03 | P0 | `./gradlew clean test`가 통과해야 한다. |
| NFR-04 | P1 | MyBatis/JPA 중 하나만 활성화될 때 중복 bean 충돌이 없어야 한다. |
| NFR-05 | P1 | sample domain의 API 동작은 persistence 선택과 무관하게 동일해야 한다. |

## Scope
- **포함**: 모듈 추가, Gradle 의존성 정리, JPA adapter 구현, MyBatis adapter profile 정리, application wiring 정리, 테스트와 문서.
- **제외**: 실제 비즈니스 도메인 설계, 운영 DB migration 도구(Flyway/Liquibase) 도입, 성능 튜닝, 대규모 schema 설계.
