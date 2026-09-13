# 고객별 커스터마이징 아키텍처 및 확장 지점 가이드 (Customization Guide)

이 문서는 단일 코드베이스에서 고객별 요구사항을 안전하게 격리·조립·배포하기 위한 커스터마이징 아키텍처 원칙과 확장 지점 규약을 정의합니다.

---

## 1. 커스터마이징 핵심 원칙

1. **공통 로직 유지 및 "덧붙이기" 확장**:
   - 비즈니스 핵심 로직은 `core/application`(`SampleService`, `UserService`)에 유지하며, 고객별 분기(`if (customer == "acme")`)를 공통 코드에 직접 작성하지 않습니다.
   - 확장은 "고치기"가 아니라 도메인 정책 인터페이스(`List<SampleSavePolicy>`, `List<UserSavePolicy>`) 구현체를 "덧붙이기" 방식으로 주입받아 순차 적용합니다.
2. **산출물 격리 조립**:
   - 빌드 시 `-Pcustomer=<고객>` 프로퍼티를 전달하면 해당 고객 모듈만 `runtimeOnly`로 포함되어 bootJar 산출물에 패키징됩니다.
   - 미지정 빌드 또는 다른 고객 빌드에는 타 고객의 코드가 전혀 포함되지 않습니다.
3. **고객 전용 데이터 제약 (확장 엔티티는 다음 조각)**:
   - 고객 전용 컬럼이나 테이블을 공통 엔티티에 직접 추가하는 행위는 엄격히 금지됩니다.
   - 확장 엔티티 및 고객별 별도 스키마 매핑은 후속 설계 조각에서 다룹니다.

---

## 2. 고객 모듈 규약

- **모듈 위치**: `customers/<고객명>` (예: `customers/acme`)
- **패키지 명명**: `cc.midolog.customers.<고객명>`
- **자동 구성 필수**: Spring Boot의 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 `@AutoConfiguration` 클래스를 등록합니다.
- **스테레오타입 전면 금지 (컴포넌트 스캔 침범 방지)**:
  - 구체 클래스에 `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration` 어노테이션 사용을 금지합니다.
  - 모든 빈은 `@AutoConfiguration` 클래스의 `@Bean` 메서드로 노출하며, `@ConditionalOnProperty(name = ["app.customer"], havingValue = "<고객명>")` 조건을 명시해야 합니다 (`AcmeCustomerHasNoStereotypeTest` 가드).

---

## 3. 확장 지점 표 (Extension Points)

| 구분 | 대상 계약 / 인터페이스 | 설명 | 구현 방식 |
|------|-----------------------|------|----------|
| **도메인 정책** | `cc.midolog.sample.policy.SampleSavePolicy` | 샘플 저장 직전 데이터 변환/검증 | `order` 오름차순 순차 적용, 프레임워크 비의존 순수 Kotlin |
| **도메인 정책** | `cc.midolog.user.policy.UserSavePolicy` | 사용자 저장 직전 데이터 변환/검증 | `order` 오름차순 순차 적용, 프레임워크 비의존 순수 Kotlin |
| **포트 오버라이드** | `cc.midolog.sample.port.cache.SampleCachePort` | 기본 캐시 어댑터 교체 | 기본 어댑터(`RedisSampleCacheAdapter`)의 `@ConditionalOnMissingBean`에 따라 고객 전용 `@Bean`이 우선 등록됨 |
| **엔드포인트** | `org.springframework.web.reactive.function.server.RouterFunction` | 고객 전용 WebFlux API 엔드포인트 | `coRouter { GET(...) { ... } }` 빈으로 등록하여 전용 라우트 제공 |
| **고객 식별자** | `cc.midolog.customer.CustomerDescriptor` | 활성화된 고객 식별 메타데이터 | `CustomerDescriptor(name = "<고객명>")` 빈 등록 |

---

## 4. 기동 시 Fail-Fast 검증 (4대 케이스)

`CustomerAutoConfiguration`의 `BeanFactoryPostProcessor`는 애플리케이션 기동 초기에 프로퍼티와 실린 모듈의 일치 여부를 조기 검증합니다:

1. **`app.customer=<고객>` 설정 + `CustomerDescriptor` 빈 없음**:
   - 실린 고객 모듈이 없거나 불일치하므로 즉시 기동 실패 (오류 메시지에 `app.customer`와 지정된 고객명 포함).
2. **`CustomerDescriptor` 빈 존재 + `app.customer` 프로퍼티 없음**:
   - 고객 모듈이 클래스패스에 존재하나 활성화 프로퍼티가 누락되었으므로 즉시 기동 실패.
3. **`app.customer=acme` 프로퍼티 + `CustomerDescriptor("beta")` 등록**:
   - 활성화 프로퍼티와 실제 실린 고객 모듈이 불일치하므로 즉시 기동 실패.
4. **둘 다 없음**:
   - 공통 기본 모드로 정상 기동.

---

## 5. 빌드 및 산출물 대조 검증

```bash
# 기본 bootJar 빌드 (고객 모듈 제외)
./gradlew :core:application:bootJar

# Acme 고객 전용 bootJar 빌드
./gradlew :core:application:bootJar -Pcustomer=acme

# 산출물 대조 검증 스크립트 실행
./scripts/build-customer.sh
```
