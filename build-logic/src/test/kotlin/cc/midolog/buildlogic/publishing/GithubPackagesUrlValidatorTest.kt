package cc.midolog.buildlogic.publishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GithubPackagesUrlValidatorTest {

    @Test
    fun `valid https maven pkg github com url passes validation`() {
        val url = "https://maven.pkg.github.com/dev-inho/backend-init"
        val uri = GithubPackagesUrlValidator.validate(url)
        assertNotNull(uri)
        assertEquals("https", uri.scheme)
        assertEquals("maven.pkg.github.com", uri.host)
        assertEquals("/dev-inho/backend-init", uri.path)
    }

    @Test
    fun `trailing slash in valid url is normalized and accepted`() {
        val url = "https://maven.pkg.github.com/dev-inho/backend-init/"
        val uri = GithubPackagesUrlValidator.validate(url)
        assertNotNull(uri)
    }

    @Test
    fun `insecure http scheme is rejected with security exception`() {
        val url = "http://maven.pkg.github.com/dev-inho/backend-init"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("HTTPS scheme"))
    }

    @Test
    fun `arbitrary attacker host is rejected with security exception`() {
        val url = "https://attacker.com/dev-inho/backend-init"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("maven.pkg.github.com"))
    }

    @Test
    fun `different repository path on github packages is rejected with security exception`() {
        val url = "https://maven.pkg.github.com/other-org/other-repo"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("/dev-inho/backend-init"))
    }

    @Test
    fun `url with userInfo is rejected to prevent credential embedding`() {
        val url = "https://user:token@maven.pkg.github.com/dev-inho/backend-init"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("userInfo"))
    }

    @Test
    fun `url with query parameters is rejected`() {
        val url = "https://maven.pkg.github.com/dev-inho/backend-init?token=leaked"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("query parameters"))
    }

    @Test
    fun `url with fragment is rejected`() {
        val url = "https://maven.pkg.github.com/dev-inho/backend-init#fragment"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("fragment"))
    }

    @Test
    fun `url with non-standard port is rejected`() {
        val url = "https://maven.pkg.github.com:8443/dev-inho/backend-init"
        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url)
        }
        assert(ex.message!!.contains("standard HTTPS port"))
    }

    @Test
    fun `local mock test url is allowed only when explicit test flag is enabled`() {
        val testUrl = "http://127.0.0.1:9999/dev-inho/backend-init"

        // 기본 상태에서는 거부
        assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(testUrl, allowInsecureTestUrl = false)
        }

        // 테스트 플래그 활성화 시 루프백 주소 허용
        val uri = GithubPackagesUrlValidator.validate(testUrl, allowInsecureTestUrl = true)
        assertEquals("127.0.0.1", uri.host)
        assertEquals(9999, uri.port)
    }

    @Test
    fun `random secret in userInfo is rejected and secret is never leaked in message or cause`() {
        val randomSecret = "SECRET_" + java.util.UUID.randomUUID().toString()
        val url = "https://dummyUser:$randomSecret@maven.pkg.github.com/dev-inho/backend-init"

        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url, configKey = "customRepoUrlKey")
        }

        val message = ex.message ?: ""
        val causeMessage = ex.cause?.message ?: ""

        // 비밀이 최상위 메시지와 원인(cause)에 절대 포함되지 않아야 함
        org.junit.jupiter.api.Assertions.assertFalse(message.contains(randomSecret), "Secret must not appear in exception message")
        org.junit.jupiter.api.Assertions.assertFalse(causeMessage.contains(randomSecret), "Secret must not appear in cause message")
        org.junit.jupiter.api.Assertions.assertNull(ex.cause, "Cause should be null to avoid stacktrace leakage")

        // 설정 키와 위반 종류만 기술되어야 함
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("customRepoUrlKey"), "Message must identify the config key")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("userInfo"), "Message must identify userInfo violation")
    }

    @Test
    fun `random secret in query parameter is rejected and secret is never leaked in message or cause`() {
        val randomSecret = "SECRET_QUERY_" + java.util.UUID.randomUUID().toString()
        val url = "https://maven.pkg.github.com/dev-inho/backend-init?token=$randomSecret"

        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url, configKey = "customRepoUrlKey")
        }

        val message = ex.message ?: ""
        org.junit.jupiter.api.Assertions.assertFalse(message.contains(randomSecret), "Secret must not appear in exception message")
        org.junit.jupiter.api.Assertions.assertNull(ex.cause, "Cause should be null to avoid stacktrace leakage")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("customRepoUrlKey"), "Message must identify the config key")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("query parameters"), "Message must identify query violation")
    }

    @Test
    fun `random secret in fragment is rejected and secret is never leaked in message or cause`() {
        val randomSecret = "SECRET_FRAG_" + java.util.UUID.randomUUID().toString()
        val url = "https://maven.pkg.github.com/dev-inho/backend-init#$randomSecret"

        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url, configKey = "customRepoUrlKey")
        }

        val message = ex.message ?: ""
        org.junit.jupiter.api.Assertions.assertFalse(message.contains(randomSecret), "Secret must not appear in exception message")
        org.junit.jupiter.api.Assertions.assertNull(ex.cause, "Cause should be null to avoid stacktrace leakage")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("customRepoUrlKey"), "Message must identify the config key")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("fragment"), "Message must identify fragment violation")
    }

    @Test
    fun `random secret in path is rejected and secret is never leaked in message or cause`() {
        val randomSecret = "SECRET_PATH_" + java.util.UUID.randomUUID().toString()
        val url = "https://maven.pkg.github.com/dev-inho/backend-init/$randomSecret"

        val ex = assertThrows(SecurityException::class.java) {
            GithubPackagesUrlValidator.validate(url, configKey = "customRepoUrlKey")
        }

        val message = ex.message ?: ""
        org.junit.jupiter.api.Assertions.assertFalse(message.contains(randomSecret), "Secret must not appear in exception message")
        org.junit.jupiter.api.Assertions.assertNull(ex.cause, "Cause should be null to avoid stacktrace leakage")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("customRepoUrlKey"), "Message must identify the config key")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("/dev-inho/backend-init"), "Message must identify path violation")
    }

    @Test
    fun `malformed url with random secret is rejected and secret is never leaked in message or cause`() {
        val randomSecret = "SECRET_MALFORMED_" + java.util.UUID.randomUUID().toString()
        val url = "https://user:$randomSecret@[invalid-host-bracket/dev-inho/backend-init"

        val ex = assertThrows(IllegalArgumentException::class.java) {
            GithubPackagesUrlValidator.validate(url, configKey = "customRepoUrlKey")
        }

        val message = ex.message ?: ""
        val causeMessage = ex.cause?.message ?: ""

        org.junit.jupiter.api.Assertions.assertFalse(message.contains(randomSecret), "Secret must not appear in exception message")
        org.junit.jupiter.api.Assertions.assertFalse(causeMessage.contains(randomSecret), "Secret must not appear in cause message")
        org.junit.jupiter.api.Assertions.assertNull(ex.cause, "Cause should be null to avoid URISyntaxException stacktrace leakage")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("customRepoUrlKey"), "Message must identify the config key")
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("malformed URI"), "Message must identify malformed URI violation")
    }
}
