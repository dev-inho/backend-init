package cc.midolog.file.port.storage

import cc.midolog.file.model.PresignedRequest

/**
 * 사전 서명(Presigned URL) 기능을 지원하는 객체 스토리지 전용 도메인 포트.
 *
 * 로컬 파일 시스템 등 서명 capability를 지원하지 않는 프로바이더에서는 isSupported가 false이며,
 * 해당 환경에서 서명 메서드 호출 시 지원하지 않는 동작 예외를 발생시킨다.
 */
interface FilePresignPort {
    /**
     * 현재 활성화된 스토리지 프로바이더의 사전 서명 기능 지원 여부.
     */
    val isSupported: Boolean

    /**
     * 클라이언트가 스토리지에 파일을 직접 업로드할 수 있도록 사전 서명된 업로드 요청 명세를 발급한다.
     * 키, 서명 유효 만료 시간(초), 예상 파일 크기, 콘텐츠 타입을 기반으로 서명을 생성한다.
     */
    suspend fun presignUpload(
        key: String,
        expirationSeconds: Long,
        expectedSize: Long?,
        contentType: String,
    ): PresignedRequest

    /**
     * 클라이언트가 스토리지로부터 파일을 직접 다운로드할 수 있도록 사전 서명된 다운로드 요청 명세를 발급한다.
     * 키와 서명 유효 만료 시간(초)을 기반으로 GET 명세를 발급한다.
     */
    suspend fun presignDownload(
        key: String,
        expirationSeconds: Long,
    ): PresignedRequest
}
