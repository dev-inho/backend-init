package cc.midolog.storage.file.s3

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.util.UUID

class S3FilePresignAdapterTest {

    private val presigner = S3Presigner.builder()
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create("http://localhost:9000"))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummyAccess", "dummySecret")))
        .build()

    private val adapter = S3FilePresignAdapter(presigner, "test-bucket")

    @Test
    fun `isSupported는 항상 true여야 한다`() {
        assertTrue(adapter.isSupported)
    }

    @Test
    fun `UUID가 아닌 키는 서명 발급을 거부해야 한다`() = runBlocking {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                adapter.presignUpload("not-uuid", 300L, 100L, "text/plain")
            }
        }
    }

    @Test
    fun `presignUpload는 서명된 URL과 필요한 헤더를 포함하는 PresignedRequest를 반환해야 한다`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val result = adapter.presignUpload(key, 300L, 1024L, "image/png", "sample-sha256")

        assertNotNull(result.url)
        assertTrue(result.url.contains(key))
        assertEquals("PUT", result.method)
        assertEquals(300L, result.expirationSeconds)
        assertEquals("image/png", result.requiredHeaders["Content-Type"])
        assertEquals("sample-sha256", result.requiredHeaders["x-amz-meta-sha256"])
    }

    @Test
    fun `presignDownload는 GET 명세를 반환해야 한다`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val result = adapter.presignDownload(key, 600L)

        assertNotNull(result.url)
        assertTrue(result.url.contains(key))
        assertEquals("GET", result.method)
        assertEquals(600L, result.expirationSeconds)
    }
}
