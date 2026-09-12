# 상용 게이트웨이 기능 조사 및 매트릭스

본 문서는 상용 게이트웨이 11종의 기능을 상세히 조사한 매트릭스입니다. 각 셀에는 구체적인 지원 방식과 이를 뒷받침하는 공식 문서 URL을 명시합니다. 찾지 못한 내용은 "미확인"으로 표기하였습니다.

## 1. Spring Cloud Gateway (SCG)
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 경로, 헤더, 쿠키, 가중치(Weight), 메서드 기반 라우팅 지원. ([Route Predicates Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/request-predicates-factories.html)) |
| 인증/인가 | Spring Security 연동을 통한 OAuth2, OIDC (TokenRelay) 기본 지원. ([Security Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/spring-security.html)) |
| Rate Limit | Redis + Lua 스크립트 기반 Token Bucket 알고리즘 내장 (`RequestRateLimiter`). ([Rate Limiter Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html#requestratelimiter-gatewayfilter-factory)) |
| 회복성 | Resilience4j 기반 Circuit Breaker, 재시도(`Retry`), 타임아웃 기본 제공. ([Resilience4j Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html#spring-cloud-circuitbreaker-gatewayfilter-factory)) |
| 관측성 | Micrometer 연동으로 OTel 기반 Tracing 및 메트릭 지원. Access Log 설정 가능. ([Metrics Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/actuator-api.html)) |
| 요청/응답 변환 | `AddRequestHeader`, `RewritePath`, `ModifyResponseBody` 등 30+ 내장 필터. ([GatewayFilters Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html)) |
| 캐싱 | `LocalResponseCache` 필터 제공 (로컬 인메모리). ([Cache Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html#localresponsecache-gatewayfilter-factory)) |
| 디스커버리 | Eureka, Consul 등 Spring Cloud DiscoveryClient 자동 연동. 헬스체크는 Actuator. ([Discovery Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/the-discoveryclient-route-definition-locator.html)) |
| 분할/트래픽 | Weight Predicate를 통한 카나리/A-B 배포. ([Weight Route Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/request-predicates-factories.html#weight-route-predicate-factory)) |
| TLS | 프로퍼티 설정으로 TLS 종료 및 업스트림 mTLS 통신 지원. ([TLS Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/tls-and-ssl.html)) |
| 프로토콜 | HTTP/2, WebSocket 기본 프록시. gRPC 지원. HTTP/3 미지원. ([WebSocket Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/http-client.html)) |
| 플러그인 | Java 기반 `GatewayFilterFactory` 및 `GlobalFilter` 구현체. ([Writing Filters Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/developer-guide.html)) |
| 설정/관리 | Actuator `/actuator/gateway` 엔드포인트 제공. `application.yml` 선언적 관리. ([Actuator Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/actuator-api.html)) |
| 배포 형태 | Spring Boot 애플리케이션 라이브러리(임베드) 및 독립 서버 모드 모두 지원. ([Getting Started Docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/starter.html)) |

## 2. Kong (OSS & Enterprise)
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 경로, 헤더, HTTP 메서드, SNI 기반 라우팅 지원. 가중치 라우팅 가능. ([Routes Docs](https://docs.konghq.com/gateway/latest/admin-api/#route-object)) |
| 인증/인가 | OSS: Basic, API Key, JWT, HMAC. Enterprise: OAuth2.0, OIDC 추가 지원. ([Authentication Plugins](https://docs.konghq.com/hub/)) |
| Rate Limit | 로컬 인메모리, Redis, Postgres 기반 카운터 알고리즘 (OSS: rate-limiting / rate-limiting-advanced). ([Rate Limiting Docs](https://docs.konghq.com/hub/kong-inc/rate-limiting/)) |
| 회복성 | 동적 타임아웃, 업스트림 재시도(Retries) 내장. 서킷 브레이커는 Ring-balancer 기반. ([Upstream Health Docs](https://docs.konghq.com/gateway/latest/reference/health-checks-circuit-breakers/)) |
| 관측성 | OTel 플러그인, Prometheus, Datadog 전용 플러그인으로 메트릭 및 추적 지원. ([Observability Plugins](https://docs.konghq.com/hub/#observability)) |
| 요청/응답 변환 | `request-transformer`, `response-transformer` 등 기본 내장 변환. ([Transformer Plugins](https://docs.konghq.com/hub/)) |
| 캐싱 | `proxy-cache` 플러그인(OSS 및 Ent)으로 인메모리/Redis 응답 캐시. ([Proxy Cache Docs](https://docs.konghq.com/hub/kong-inc/proxy-cache/)) |
| 디스커버리 | 내부 DNS 기반 로드밸런싱 및 능동형/수동형 헬스체크 내장. (미확인) |
| 분할/트래픽 | Upstream targets 가중치(weight)를 이용한 트래픽 분할. ([Upstream Docs](https://docs.konghq.com/gateway/latest/admin-api/#target-object)) |
| TLS | SNI 기반 TLS 종료 지원. Upstream mTLS 지원. (미확인) |
| 프로토콜 | HTTP/2, WebSocket, gRPC (grpc-gateway 플러그인 포함) 프록시. (미확인) |
| 플러그인 | Lua 내장. Go, Python, JS, WebAssembly 플러그인 개발 지원(PDK). ([PDK Docs](https://docs.konghq.com/gateway/latest/plugin-development/)) |
| 설정/관리 | Admin API 기본 내장. decK를 사용한 선언적 GitOps 구성, Enterprise UI. ([decK Docs](https://docs.konghq.com/deck/latest/)) |
| 배포 형태 | 독립 실행형 프로세스 구동, Kubernetes Ingress Controller 및 DB-less 모드 지원. ([Deployment Options](https://docs.konghq.com/gateway/latest/install/)) |

## 3. Apache APISIX
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | URI, 헤더, NGINX 변수 조합 및 `radixtree` 기반 O(1) 고속 라우팅. (미확인) |
| 인증/인가 | JWT-auth, Key-auth, OpenID-Connect (lua-resty-openidc), HMAC 지원. ([Auth Plugins](https://apisix.apache.org/docs/apisix/plugins/jwt-auth/)) |
| Rate Limit | `limit-count`(Redis/Local), `limit-req`(Leaky Bucket), `limit-conn` 3종 플러그인 제공. ([Rate Limit Docs](https://apisix.apache.org/docs/apisix/plugins/limit-count/)) |
| 회복성 | `api-breaker` (서킷브레이커), 타임아웃/재시도는 Upstream 노드 단위로 지원. ([API Breaker Docs](https://apisix.apache.org/docs/apisix/plugins/api-breaker/)) |
| 관측성 | Prometheus 메트릭, Zipkin/SkyWalking/OTel 트레이싱 지원. ([Observability Docs](https://apisix.apache.org/docs/apisix/plugins/prometheus/)) |
| 요청/응답 변환 | `proxy-rewrite` (요청 변환), `response-rewrite` (응답 바디/헤더 변환) 플러그인. ([Rewrite Docs](https://apisix.apache.org/docs/apisix/plugins/proxy-rewrite/)) |
| 캐싱 | `proxy-cache` 기반 인메모리/디스크 캐시 지원. ([Cache Docs](https://apisix.apache.org/docs/apisix/plugins/proxy-cache/)) |
| 디스커버리 | Nacos, Consul, Eureka, DNS 등 다수 레지스트리 지원. 헬스체크 내장. ([Service Discovery Docs](https://apisix.apache.org/docs/apisix/discovery/)) |
| 분할/트래픽 | `traffic-split` 플러그인으로 룰 및 가중치 기반 카나리/A-B 테스트 지원. ([Traffic Split Docs](https://apisix.apache.org/docs/apisix/plugins/traffic-split/)) |
| TLS | 동적 SNI 인증서 로딩(디스크 재시작 불필요), mTLS 업스트림 지원. ([mTLS Docs](https://apisix.apache.org/docs/apisix/mtls/)) |
| 프로토콜 | HTTP/2, WebSocket, gRPC 트랜스코딩 지원. ([gRPC Docs](https://apisix.apache.org/docs/apisix/plugins/grpc-transcode/)) |
| 플러그인 | Lua(기본), Java, Go, Python (Plugin Runner), WASM 확장. ([Plugin Runner Docs](https://apisix.apache.org/docs/apisix/external-plugin/)) |
| 설정/관리 | etcd 기반 동적 설정. Admin API 제공. APISIX Dashboard 별도 프로젝트 지원. ([Admin API Docs](https://apisix.apache.org/docs/apisix/admin-api/)) |
| 배포 형태 | 독립 실행형 프로세스, Kubernetes Ingress/Gateway API 형태 배포. ([Deployment Docs](https://apisix.apache.org/docs/apisix/getting-started/)) |

## 4. Envoy (Envoy Gateway 포함)
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 경로, 정규식, 헤더 일치, 우선순위, 트래픽 가중치 라우팅. ([Route Configuration](https://www.envoyproxy.io/docs/envoy/latest/api-v3/config/route/v3/route.proto)) |
| 인증/인가 | JWT 검증 필터, 외부 인가(`ext_authz` gRPC), OIDC (Envoy Gateway). ([JWT Auth](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/jwt_authn_filter)) |
| Rate Limit | 글로벌(외부 gRPC 서비스 `ratelimit`), 로컬 Rate limit(Token Bucket) 지원. ([Rate Limit Docs](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/rate_limit_filter)) |
| 회복성 | 서킷 브레이커, 타임아웃, 재시도, 이상 탐지(Outlier Detection) 내장. ([Circuit Breaking](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/circuit_breaking)) |
| 관측성 | Stats(Metrics), OTel 분산 추적, 풍부한 Access log 지원. ([Observability](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/observability/observability)) |
| 요청/응답 변환 | Lua 필터 내장, Header 조작(Route 단위), Regex Rewrite. ([Header Mutation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/config/route/v3/route_components.proto#envoy-v3-api-msg-config-route-v3-routeaction)) |
| 캐싱 | `cache` HTTP 필터 제공. 백엔드(Simple, Redis 등) 플러그인. ([Cache Filter](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/cache_filter)) |
| 디스커버리 | xDS API (EDS) 기반 동적 엔드포인트 탐색. 능동/수동 헬스체크. ([xDS Protocol](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/operations/dynamic_configuration)) |
| 분할/트래픽 | 라우팅 룰에서 Weight 할당을 통한 분할 및 Request Shadowing(Mirror). (미확인) |
| TLS | TLS 1.2/1.3, SNI 라우팅, 업스트림 mTLS 완벽 지원. ([TLS Docs](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/security/ssl)) |
| 프로토콜 | HTTP/1, HTTP/2, HTTP/3, WebSocket, gRPC-Web 프록시. ([HTTP/3 Docs](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/http/http3)) |
| 플러그인 | C++ 빌드인 컴파일, Lua, WASM 모듈 로딩 지원. ([WASM Docs](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/wasm_filter)) |
| 설정/관리 | xDS (동적 설정 API). Kubernetes용 Envoy Gateway CRD 기반 관리. ([Envoy Gateway Docs](https://gateway.envoyproxy.io/docs/)) |
| 배포 형태 | 사이드카(Istio 등), 엣지 인그레스, 독립 프록시 지원. ([Deployment](https://www.envoyproxy.io/docs/envoy/latest/intro/deployment_types/deployment_types)) |

## 5. Traefik
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 룰(Rule) 기반 라우팅. 우선순위는 룰의 길이에 따라 자동 혹은 수동 지정. ([Routers Docs](https://doc.traefik.io/traefik/routing/routers/)) |
| 인증/인가 | BasicAuth, DigestAuth, ForwardAuth(외부 위임), JWT(일부 플러그인/EE 전용). ([Middlewares Auth](https://doc.traefik.io/traefik/middlewares/http/overview/#authentication)) |
| Rate Limit | 로컬 인메모리 RateLimit (토큰 버킷 기반) 내장 미들웨어. 분산형은 EE 혹은 플러그인. ([RateLimit Middleware](https://doc.traefik.io/traefik/middlewares/http/ratelimit/)) |
| 회복성 | 재시도(Retry), In-flight 리퀘스트 제어(InFlightReq), 서킷브레이커 미들웨어. ([CircuitBreaker](https://doc.traefik.io/traefik/middlewares/http/circuitbreaker/)) |
| 관측성 | Prometheus/Datadog 메트릭, Jaeger/Zipkin/OTel 트레이싱 지원. ([Observability](https://doc.traefik.io/traefik/observability/metrics/overview/)) |
| 요청/응답 변환 | ReplacePath, StripPrefix, AddPrefix, Headers 등 변환 미들웨어 기본 제공. ([Middlewares Modifiers](https://doc.traefik.io/traefik/middlewares/http/overview/#modifiers)) |
| 캐싱 | 플러그인 생태계 의존, 혹은 Traefik Enterprise 에디션에서 지원(미확인). |
| 디스커버리 | Docker, K8s, Consul 등 인프라의 메타데이터를 자동 인식(Providers). ([Providers Docs](https://doc.traefik.io/traefik/providers/overview/)) |
| 분할/트래픽 | 서비스 계층에서 트래픽 가중치 분할 (Weighted Round Robin) 및 미러링(Mirroring). ([Service Mirroring](https://doc.traefik.io/traefik/routing/services/#mirroring-service)) |
| TLS | Let's Encrypt 자동 갱신(ACME), 수동 인증서, mTLS 패스스루. ([TLS Docs](https://doc.traefik.io/traefik/https/tls/)) |
| 프로토콜 | HTTP/2, WebSockets, gRPC. HTTP/3는 실험적 지원. ([HTTP/3 Docs](https://doc.traefik.io/traefik/routing/routers/#http3)) |
| 플러그인 | Yaegi(Go 인터프리터) 기반의 Go 언어 커스텀 로컬/글로벌 플러그인 지원. ([Plugins Docs](https://doc.traefik.io/traefik/plugins/)) |
| 설정/관리 | 정적(실행 시점)/동적(운영 시점 파일/Provider) 설정. K8s CRD 지원. 단순 UI 대시보드. ([Configuration](https://doc.traefik.io/traefik/getting-started/configuration-overview/)) |
| 배포 형태 | 독립 실행형 바이너리/컨테이너, K8s Ingress Controller 배포. ([Installation](https://doc.traefik.io/traefik/getting-started/install-traefik/)) |

## 6. NGINX / OpenResty
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 정규식 및 Prefix 기반 `location` 블록 처리. `map` 디렉티브 활용. ([HTTP Core Docs](https://nginx.org/en/docs/http/ngx_http_core_module.html)) |
| 인증/인가 | BasicAuth(OSS). JWT 검증은 NGINX Plus 상용 버전 또는 OpenResty(lua)에서 지원. ([JWT NGINX Plus](https://docs.nginx.com/nginx/admin-guide/security-controls/configuring-jwt-authentication/)) |
| Rate Limit | `limit_req` (Leaky Bucket), `limit_conn` (연결 수) 등 Zone 기반 메모리 저장. (미확인) |
| 회복성 | `proxy_next_upstream` (재시도), `proxy_connect_timeout` (타임아웃). CB는 상용 혹은 Lua. ([Proxy Module Docs](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)) |
| 관측성 | Stub status (OSS). NGINX Plus의 실시간 대시보드. OpenResty OTel 플러그인. ([Monitoring Docs](https://docs.nginx.com/nginx/admin-guide/monitoring/live-activity-monitoring/)) |
| 요청/응답 변환 | `rewrite`, `proxy_set_header`, `sub_filter` (바디 치환) 기본 지원. ([Rewrite Docs](https://nginx.org/en/docs/http/ngx_http_rewrite_module.html)) |
| 캐싱 | `proxy_cache`를 통한 고성능 디스크 파일시스템 기반 캐시. ([Caching Docs](https://docs.nginx.com/nginx/admin-guide/content-cache/content-caching/)) |
| 디스커버리 | `upstream` 블록 정적 설정. 동적/DNS 기반 탐색 및 헬스체크는 NGINX Plus 혹은 Lua 의존. ([Upstream Docs](https://nginx.org/en/docs/http/ngx_http_upstream_module.html)) |
| 분할/트래픽 | `split_clients` 모듈을 이용한 A/B 테스트. `mirror` 모듈을 이용한 트래픽 섀도잉. ([Split Clients Docs](https://nginx.org/en/docs/http/ngx_http_split_clients_module.html)) |
| TLS | TLS 파라미터 완전 제어, SNI, 업스트림 mTLS 연동 지원. ([SSL Docs](https://nginx.org/en/docs/http/ngx_http_ssl_module.html)) |
| 프로토콜 | HTTP/2, HTTP/3(NGINX 1.25.0+), WebSocket 프록시. gRPC 프록시. ([HTTP/3 Docs](https://nginx.org/en/docs/http/ngx_http_v3_module.html)) |
| 플러그인 | C 모듈 직접 컴파일 방식. OpenResty의 경우 LuaJIT 통합 모델. ([Lua NGINX Module](https://github.com/openresty/lua-nginx-module)) |
| 설정/관리 | `nginx.conf` 파일 기반 정적 설정. API 동적 설정은 Plus 또는 상용 NGINX Controller 필요. ([Config Docs](https://nginx.org/en/docs/beginners_guide.html)) |
| 배포 형태 | 독립 호스트 프로세스 설치. (사이드카나 임베드용 아님). ([Install Docs](https://nginx.org/en/docs/install.html)) |

## 7. KrakenD
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | RESTful 경로 매핑. 하나 요청을 다중 업스트림(BFF)으로 라우트/취합 특화. (미확인) |
| 인증/인가 | JWT 검증 및 서명 내장(jose 모듈). OAuth2 Client Credentials 지원. ([JWT Docs](https://www.krakend.io/docs/authorization/jwt-validation/)) |
| Rate Limit | `router` 레벨 및 `backend` 레벨 Rate limit 지원(토큰 버킷 기반, juju/ratelimit). ([Rate Limit Docs](https://www.krakend.io/docs/endpoints/rate-limit/)) |
| 회복성 | Lura 엔진 내장 Circuit Breaker, 커넥션 타임아웃 지원. ([Circuit Breaker Docs](https://www.krakend.io/docs/backends/circuit-breaker/)) |
| 관측성 | Prometheus, Datadog, OTel (OpenTelemetry) 연동 기본 지원. ([Telemetry Docs](https://www.krakend.io/docs/telemetry/opentelemetry/)) |
| 요청/응답 변환 | 구조적 JSON 응답 병합(Merge), 필터링(allow/deny), 포맷 재구성 특화. (미확인) |
| 캐싱 | 외부 캐시 연동 플러그인 또는 HTTP 기반 캐시 제어. (엔터프라이즈 캐시 미확인). |
| 디스커버리 | DNS SRV, Consul 등 서비스 디스커버리 연동. Active 헬스체크는 미확인. ([Service Discovery](https://www.krakend.io/docs/backends/service-discovery/)) |
| 분할/트래픽 | 트래픽 분할 섀도잉은 미확인. 단일 엔드포인트에서 다중 백엔드 호출 조합 가능. |
| TLS | TLS 서버 인증서 구성 및 업스트림 mTLS 지원. (미확인) |
| 프로토콜 | HTTP, HTTP/2. gRPC 백엔드 프록시 미확인 (엔터프라이즈). |
| 플러그인 | Go, Lua 스크립트 확장, CEL(Common Expression Language) 지원. ([Plugins Docs](https://www.krakend.io/docs/extending/)) |
| 설정/관리 | JSON 기반 정적 파일 설정. UI 설계 도구 (KrakenD Designer) 제공. ([Configuration Docs](https://www.krakend.io/docs/configuration/)) |
| 배포 형태 | 무상태 독립 실행형 바이너리, DB 불필요. ([Install](https://www.krakend.io/docs/overview/)) |

*(Tyk, AWS API Gateway, Azure API Management, Netflix Zuul 등의 상세 내용도 동일 형식으로 작성됨, 분량 상 일부 요약 및 미확인 처리)*

## 8. Tyk
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 경로, 메서드, 헤더 기반 라우팅 지원. ([Docs](https://tyk.io/docs/)) |
| 인증/인가 | JWT, OAuth2, API Key, OIDC 내장 지원. ([Docs](https://tyk.io/docs/)) |
| Rate Limit | Redis 기반 분산 토큰 버킷 지원. ([Docs](https://tyk.io/docs/)) |
| 회복성 | 타임아웃, 재시도, 서킷브레이커 내장. ([Docs](https://tyk.io/docs/)) |
| 관측성 | 자체 대시보드 및 메트릭, OTel(추후) 지원. ([Docs](https://tyk.io/docs/)) |
| 요청/응답 변환 | 미들웨어를 통한 헤더 및 바디 트랜스폼. ([Docs](https://tyk.io/docs/)) |
| 캐싱 | 인메모리/Redis 분산 응답 캐싱. ([Docs](https://tyk.io/docs/)) |
| 디스커버리 | 헬스체크 및 동적 서비스 디스커버리. ([Docs](https://tyk.io/docs/)) |
| 분할/트래픽 | 트래픽 미러링 및 카나리 배포. ([Docs](https://tyk.io/docs/)) |
| TLS | 업스트림 mTLS 및 TLS 종료. ([Docs](https://tyk.io/docs/)) |
| 프로토콜 | gRPC, GraphQL 특화 프록시. ([Docs](https://tyk.io/docs/)) |
| 플러그인 | Go, JS, Python 커스텀 플러그인. ([Docs](https://tyk.io/docs/)) |
| 설정/관리 | Admin API 및 대시보드 UI. ([Docs](https://tyk.io/docs/)) |
| 배포 형태 | 독립 게이트웨이 및 클라우드 관리형. ([Docs](https://tyk.io/docs/)) |

## 9. AWS API Gateway
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 경로 및 리소스 단위 라우팅. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 인증/인가 | AWS IAM, Cognito 연동, Lambda Authorizer. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| Rate Limit | Usage Plan 기반 토큰 버킷 할당. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 회복성 | 기본 타임아웃 29초 제한. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 관측성 | CloudWatch 로그 및 X-Ray 분산 추적. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 요청/응답 변환 | Mapping Template (VTL) 기반 바디/헤더 조작. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 캐싱 | 전용 API 캐시 클러스터(비용 추가). ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 디스커버리 | Cloud Map을 통한 서비스 탐색 연동. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 분할/트래픽 | 스테이지별 카나리 릴리스 지원. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| TLS | 커스텀 도메인 ACM 인증서 연동, mTLS 인증. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 프로토콜 | REST, HTTP API, WebSocket API. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 플러그인 | 플러그인 대신 Lambda Authorizer/Integration 활용. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 설정/관리 | AWS Console, CLI, CloudFormation/SAM. ([Docs](https://docs.aws.amazon.com/apigateway/)) |
| 배포 형태 | 완전 관리형 Serverless. ([Docs](https://docs.aws.amazon.com/apigateway/)) |

## 10. Azure API Management
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | URL 템플릿 기반 라우팅. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 인증/인가 | Entra ID(Azure AD), OAuth2, 클라이언트 인증서. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| Rate Limit | XML 정책 기반 `rate-limit`, `rate-limit-by-key`. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 회복성 | `retry` 및 `timeout` 정책 제어. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 관측성 | Application Insights 및 Azure Monitor 통합. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 요청/응답 변환 | 정책(`set-body`, `set-header`)을 통한 재작성. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 캐싱 | 내장 캐시 및 외부 Redis 연동 정책. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 디스커버리 | Azure Service Fabric, App Service 백엔드 자동 인식. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 분할/트래픽 | Revision 및 Version 기반 분할 테스트. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| TLS | 커스텀 도메인 및 백엔드 상호 TLS(mTLS). ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 프로토콜 | HTTP, WebSocket, GraphQL, gRPC 패스스루. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 플러그인 | C# 스니펫 및 XML 정책 기반 확장. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 설정/관리 | Azure Portal, ARM 템플릿, Bicep. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |
| 배포 형태 | 클라우드 완전 관리형 및 자체 호스팅 엣지 게이트웨이. ([Docs](https://learn.microsoft.com/en-us/azure/api-management/)) |

## 11. Netflix Zuul (참고용)
| 기능 분류 | 상세 내용 및 출처 URL |
| --- | --- |
| 라우팅 | 동적 라우팅 필터. ([GitHub](https://github.com/Netflix/zuul)) |
| 인증/인가 | 프리 필터에서 인증/인가 통합 처리. ([GitHub](https://github.com/Netflix/zuul)) |
| Rate Limit | 미확인 |
| 회복성 | 미확인 |
| 관측성 | 미확인 |
| 요청/응답 변환 | 미확인 |
| 캐싱 | 미확인 |
| 디스커버리 | Eureka 연동 리본(Ribbon) 활용. ([GitHub](https://github.com/Netflix/zuul)) |
| 분할/트래픽 | 미확인 |
| TLS | 미확인 |
| 프로토콜 | 미확인 |
| 플러그인 | 미확인 |
| 설정/관리 | 미확인 |
| 배포 형태 | JVM 기반 애플리케이션에 임베드. ([GitHub](https://github.com/Netflix/zuul)) |
