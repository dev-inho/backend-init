# 상용 게이트웨이 기능 매트릭스

## 기능 매트릭스

| 기능 분류 | 항목 | Spring Cloud Gateway | Kong | Apache APISIX | Envoy (Gateway) | Traefik | NGINX/OpenResty | KrakenD | Tyk | AWS API Gateway | Azure API Management | Netflix Zuul (참고) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 라우팅 | 경로, 헤더, 가중치, 우선순위 | O (경로, 헤더, 가중치) | O | O | O | O (우선순위, 가중치) | O | O | O | O | O | O |
| 인증/인가 | JWT, OAuth2/OIDC, API Key, mTLS, Basic | O (TokenRelay 등) | O | O | O | O (ForwardAuth 등) | O (일부 상용/모듈) | O | O | O (Cognito/IAM) | O | O |
| Rate Limit | 알고리즘, 분산 저장소(Redis 등), 키 | O (Redis, 버킷) | O | O | O | O | O (Zone 기반) | O | O | O (Usage Plan) | O (정책) | O |
| 회복성 | CB, Retry, Timeout, Bulkhead | O (Resilience4j) | O | O | O | O | O | O (Lura) | O | O (Timeout, Retry) | O (정책) | O |
| 관측성 | Tracing, Metrics, Access Log, Health | O (Micrometer, OTel) | O | O | O | O | O | O | O | O (CloudWatch) | O (AppInsights) | O |
| 요청/응답 변환 | Header, Body, Path rewrite | O (필터) | O | O | O | O | O | O | O | O (Mapping Template) | O (정책) | O |
| 캐싱 | 응답 캐싱 | 미지원 (직접 구현 필요) | O | O | O | O (플러그인) | O | O | O | O | O | 미확인 |
| 서비스 디스커버리 | LB 알고리즘, 헬스체크 | O (Eureka, Consul) | O | O | O | O | O | O | O | O (Cloud Map 연동) | O | O (Eureka) |
| 트래픽 분할 | Canary, A/B, Mirror | O (Weight) | O | O | O | O | O | O | O | O | O | O |
| TLS | TLS 종료, mTLS 업스트림 | O | O | O | O | O | O | O | O | O | O | O |
| 프로토콜 | WS, gRPC, SSE, HTTP/2·3 | O (WS, gRPC, SSE, HTTP/2) | O | O | O (HTTP/3) | O (HTTP/3) | O (HTTP/3 실험적) | O | O | O (WS, HTTP/2) | O (WS) | 미확인 |
| 플러그인/확장 | 확장 모델 | O (Java 필터) | O (Lua, Go, WASM) | O (Lua, WASM) | O (WASM, C++) | O (Go, WASM) | O (Lua) | O (Go) | O (Go, JS, Python) | 제한적 (Lambda Authorizer) | 제한적 (정책 XML/C#) | O (Groovy/Java) |
| 설정/관리 | 관리 UI, Admin API, 선언적(GitOps) | O (Actuator, 설정파일) | O (Admin API, UI) | O (Admin API, Dashboard) | O (xDS API) | O (Dashboard, File, CRD) | O (설정파일) | O (JSON, Designer) | O (Dashboard) | O (Console, API, CloudFormation) | O (Portal, ARM) | O (Archaius) |
| 배포 형태 | 임베드(사이드카/독립) | 임베드(라이브러리), 독립 | 독립, 사이드카 | 독립, 사이드카 | 독립, 사이드카 | 독립 | 독립 | 독립 | 독립 | 완전 관리형 | 완전 관리형 | 임베드, 독립 |
| **출처 URL** | 공식 문서 | [Docs](https://docs.spring.io/spring-cloud-gateway/reference/) | [Docs](https://docs.konghq.com/gateway/latest/) | [Docs](https://apisix.apache.org/docs/apisix/) | [Docs](https://www.envoyproxy.io/docs/envoy/latest/) | [Docs](https://doc.traefik.io/traefik/) | [Docs](https://docs.nginx.com/) | [Docs](https://www.krakend.io/docs/overview/) | [Docs](https://tyk.io/docs/) | [Docs](https://docs.aws.amazon.com/apigateway/) | [Docs](https://learn.microsoft.com/en-us/azure/api-management/) | [GitHub](https://github.com/Netflix/zuul) |
