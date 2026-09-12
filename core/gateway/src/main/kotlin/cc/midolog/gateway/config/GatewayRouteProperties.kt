package cc.midolog.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 프록시 라우팅 대상 백엔드 서버 URL 설정 프로퍼티.
 *
 * `gateway.routes` 접두사로 바인딩되며, 각 프로퍼티와 환경변수 매핑은 다음과 같다:
 * - [applicationUrl]: 단일 비즈니스 애플리케이션 URL (환경변수 `GATEWAY_APPLICATION_URL`).
 * - [applicationUrls]: 여러 비즈니스 애플리케이션 인스턴스 URL 목록 (환경변수 `GATEWAY_APPLICATION_URLS`).
 *   비어 있지 않으면 라운드로빈 분산 대상 목록으로 우선 사용된다.
 * - [batchUrl]: 배치 전용 서버 URL (환경변수 `GATEWAY_BATCH_URL`).
 */
@Component
@ConfigurationProperties(prefix = "gateway.routes")
data class GatewayRouteProperties(
    var applicationUrl: String = "",
    var applicationUrls: List<String> = emptyList(),
    var batchUrl: String = "",
)
