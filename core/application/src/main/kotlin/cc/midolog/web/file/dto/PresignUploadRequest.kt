package cc.midolog.web.file.dto

/**
 * 클라이언트의 파일 업로드 사전 서명 URL 발급 요청 DTO.
 *
 * @property contentType 업로드할 파일의 MIME 미디어 타입
 * @property expectedSize 클라이언트가 예상하는 파일 크기 (바이트 단위, 선택)
 * @property expectedChecksum 클라이언트가 사전에 계산한 SHA-256 체크섬 (선택)
 */
data class PresignUploadRequest(
    val contentType: String,
    val expectedSize: Long? = null,
    val expectedChecksum: String? = null,
)
