package cc.midolog.gateway.proxy

import org.springframework.http.HttpHeaders

/** 프록시 시 전달하지 않는 hop-by-hop/Host/Content-Length 헤더를 제거한다. */
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
