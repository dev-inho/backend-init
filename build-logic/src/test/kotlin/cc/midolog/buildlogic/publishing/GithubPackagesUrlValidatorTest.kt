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
}
