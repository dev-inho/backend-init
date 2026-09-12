package cc.midolog.gateway.proxy

import org.springframework.http.HttpHeaders

/**
 * 프록시 중계 시 전송 레벨 헤더 및 불필요한 헤더를 제거하는 유틸리티.
 *
 * hop-by-hop 헤더 목록의 출처는 RFC 7230 §6.1 및 RFC 9110 §7.6.1(Connection and Hop-by-hop Headers)이다.
 * Connection, Keep-Alive, Proxy-Authenticate, Proxy-Authorization, TE, Trailer, Transfer-Encoding, Upgrade
 * 등 단일 전송 레벨 연결에 국한된 헤더는 프록시 너머로 전파될 경우 연결 상태 불일치와 프로토콜 오류를
 * 유발하므로 반드시 제거해야 한다.
 *
 * 아울러 다운스트림 가상 호스트 라우팅과의 충돌을 방지하기 위해 Host 헤더를 제거하고,
 * 프록시 중계 과정(버퍼링 등)에서 실제 전송 바이트 수와 달라져 발생하는 왜곡을 막기 위해
 * Content-Length 헤더를 제거하여 HTTP 클라이언트가 재계산하도록 유도한다.
 */
object HeaderSanitizer {
    private val HOP_BY_HOP = setOf(
        HttpHeaders.CONNECTION,
        "Keep-Alive",
        HttpHeaders.PROXY_AUTHENTICATE,
        HttpHeaders.PROXY_AUTHORIZATION,
        HttpHeaders.TE,
        HttpHeaders.TRAILER,
        HttpHeaders.TRANSFER_ENCODING,
        HttpHeaders.UPGRADE,
        HttpHeaders.HOST,
        HttpHeaders.CONTENT_LENGTH,
    ).map { it.lowercase() }.toSet()

    /**
     * 원본 헤더에서 hop-by-hop 헤더 및 Connection에 지정된 커스텀 홉 헤더를 제외한 새 [HttpHeaders]를 반환한다.
     *
     * 소문자 기준으로 사전 정의된 HOP_BY_HOP 목록과 클라이언트의 Connection 헤더 값에 나열된
     * 토큰들을 합산하여 제거 대상 헤더를 산출한 뒤 이를 걸러낸다.
     */
    fun sanitize(source: HttpHeaders): HttpHeaders {
        val cleaned = HttpHeaders()
        val connectionScoped = source[HttpHeaders.CONNECTION]
            .orEmpty()
            .flatMap { it.split(",") }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val excluded = HOP_BY_HOP + connectionScoped

        source.forEach { name, values ->
            if (name.lowercase() !in excluded) cleaned.addAll(name, values)
        }
        return cleaned
    }
}
