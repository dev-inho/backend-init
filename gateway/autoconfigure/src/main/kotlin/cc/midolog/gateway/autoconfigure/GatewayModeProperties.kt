package cc.midolog.gateway.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 게이트웨이 기동 모드(embedded, standalone, remote)를 설정하는 프로퍼티.
 *
 * `gateway.mode`는 기본값이 없으며, 미설정 시 자동 설정 컨텍스트가 기동을 거부하여
 * 배포 사고를 방지한다(fail-fast).
 */
@ConfigurationProperties(prefix = "gateway")
data class GatewayModeProperties(
    var mode: String? = null
)
