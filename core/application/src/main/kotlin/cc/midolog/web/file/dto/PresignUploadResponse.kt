package cc.midolog.web.file.dto

/**
 * 사전 서명된 업로드 요청 명세 응답 DTO.
 *
 * 서명된 URL과 HTTP 메서드, 만료 시간 및 서명 헤더 목록을 포함한다.
 * 보안 강화를 위해 toString() 호출 시 서명 URL을 마스킹하여 로그 노출을 방지한다.
 *
 * @property fileId 생성된 파일의 고유 식별자(ULID)
 * @property uploadUrl 클라이언트가 직접 PUT 요청을 전송할 사전 서명된 URL
 * @property method HTTP 메서드 (기본값: "PUT")
 * @property expirationSeconds 서명 유효 만료 시간(초)
 * @property requiredHeaders 업로드 요청 시 필수로 전송해야 할 헤더 맵
 */
data class PresignUploadResponse(
    val fileId: String,
    val uploadUrl: String,
    val method: String,
    val expirationSeconds: Long,
    val requiredHeaders: Map<String, String>,
) {
    override fun toString(): String {
        return "PresignUploadResponse(fileId='$fileId', uploadUrl='[PROTECTED_SIGNATURE_URL]', method='$method', expirationSeconds=$expirationSeconds, requiredHeaders=$requiredHeaders)"
    }
}
