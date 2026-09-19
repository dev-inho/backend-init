package cc.midolog.storage.file.s3

import cc.midolog.file.model.PresignedRequest
import cc.midolog.file.port.storage.FilePresignPort
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

/**
 * AWS SDK S3 Presigner를 활용한 사전 서명 URL 발급 어댑터.
 *
 * 클라이언트가 S3 버킷에 직접 파일을 업로드(PUT)하거나 다운로드(GET)할 수 있는 서명된 요청 명세를 생성한다.
 * 서명 헤더에 Content-Type 및 체크섬 메타데이터를 바인딩하여 업로드 시 위변조를 방지한다.
 *
 * @param s3Presigner S3 사전 서명 생성기
 * @param bucket 대상 S3 버킷 이름
 */
class S3FilePresignAdapter(
    private val s3Presigner: S3Presigner,
    private val bucket: String,
) : FilePresignPort {

    override val isSupported: Boolean = true

    private fun validateKey(key: String) {
        try {
            UUID.fromString(key)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid UUID format for storage key: $key")
        }
        if (key.contains("/") || key.contains("..") || key.contains("\\")) {
            throw SecurityException("Path traversal attempt in storage key: $key")
        }
    }

    override suspend fun presignUpload(
        key: String,
        expirationSeconds: Long,
        expectedSize: Long?,
        contentType: String,
    ): PresignedRequest {
        return presignUpload(key, expirationSeconds, expectedSize, contentType, null)
    }

    override suspend fun presignUpload(
        key: String,
        expirationSeconds: Long,
        expectedSize: Long?,
        contentType: String,
        expectedChecksum: String?,
    ): PresignedRequest {
        validateKey(key)

        val putRequestBuilder = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)

        if (expectedSize != null) {
            putRequestBuilder.contentLength(expectedSize)
        }

        if (expectedChecksum != null) {
            putRequestBuilder.metadata(mapOf("sha256" to expectedChecksum))
        }

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .putObjectRequest(putRequestBuilder.build())
            .build()

        val presigned = s3Presigner.presignPutObject(presignRequest)

        val headers = mutableMapOf<String, String>()
        presigned.httpRequest().headers().forEach { (k, v) ->
            headers[k] = v.joinToString(",")
        }
        if (!headers.containsKey("Content-Type")) {
            headers["Content-Type"] = contentType
        }
        if (expectedChecksum != null && !headers.containsKey("x-amz-meta-sha256")) {
            headers["x-amz-meta-sha256"] = expectedChecksum
        }

        return PresignedRequest(
            url = presigned.url().toString(),
            method = "PUT",
            expirationSeconds = expirationSeconds,
            requiredHeaders = headers,
        )
    }

    override suspend fun presignDownload(
        key: String,
        expirationSeconds: Long,
    ): PresignedRequest {
        validateKey(key)

        val getRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirationSeconds))
            .getObjectRequest(getRequest)
            .build()

        val presigned = s3Presigner.presignGetObject(presignRequest)

        val headers = mutableMapOf<String, String>()
        presigned.httpRequest().headers().forEach { (k, v) ->
            headers[k] = v.joinToString(",")
        }

        return PresignedRequest(
            url = presigned.url().toString(),
            method = "GET",
            expirationSeconds = expirationSeconds,
            requiredHeaders = headers,
        )
    }
}
