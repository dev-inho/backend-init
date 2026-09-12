package cc.midolog.file.model

/**
 * 서명 기능을 지원하는 원격 스토리지(S3 등)와의 직접 통신을 위한 사전 서명 요청 명세.
 *
 * 서명된 엔드포인트 URL(url), 허용된 HTTP 메서드(method), 유효 만료 시간(expirationSeconds),
 * 업로드 또는 다운로드 시 클라이언트가 요청에 반드시 포함해야 하는 서명 헤더 맵(requiredHeaders)을 담는다.
 */
data class PresignedRequest(
    val url: String,
    val method: String,
    val expirationSeconds: Long,
    val requiredHeaders: Map<String, String>,
)
