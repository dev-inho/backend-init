package cc.midolog.buildlogic.publishing

import java.net.URI

/**
 * GitHub Packages Maven 저장소 URL에 대한 엄격한 보안 검증기.
 *
 * 자격 증명(GITHUB_TOKEN 등)이 외부 공격자 서버나 안전하지 않은 엔드포인트로 유출되는 것을 차단하기 위해,
 * 오직 공인된 HTTPS maven.pkg.github.com의 지정된 저장소 경로만 허용한다.
 */
object GithubPackagesUrlValidator {

    const val DEFAULT_REPO_URL = "https://maven.pkg.github.com/dev-inho/backend-init"
    const val ALLOWED_HOST = "maven.pkg.github.com"
    const val ALLOWED_PATH_PREFIX = "/dev-inho/backend-init"

    /**
     * 지정된 URL 문자열이 안전한 GitHub Packages Maven 저장소인지 엄격하게 검증한다.
     * 안전하지 않은 스킴, 외부 호스트, userInfo, query, fragment, 비표준 포트, 타 저장소 경로가 감지되면 예외를 발생시킨다.
     */
    fun validate(url: String, allowInsecureTestUrl: Boolean = false): URI {
        val uri = try {
            URI.create(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid repository URL format: $url", e)
        }

        // 테스트 목적의 로컬 루프백 모의 서버 허용 분기 (명시적 테스트 플래그 활성화 시에만 적용)
        if (allowInsecureTestUrl) {
            val testHost = uri.host?.lowercase() ?: ""
            if (testHost == "127.0.0.1" || testHost == "localhost") {
                if (uri.rawUserInfo != null || uri.userInfo != null) {
                    throw SecurityException("Repository URL must not contain userInfo: $url")
                }
                if (uri.rawQuery != null || uri.query != null) {
                    throw SecurityException("Repository URL must not contain query parameters: $url")
                }
                if (uri.rawFragment != null || uri.fragment != null) {
                    throw SecurityException("Repository URL must not contain fragment: $url")
                }
                return uri
            }
        }

        // 1. 스킴 검증: 반드시 HTTPS만 허용
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            throw SecurityException(
                "GitHub Packages URL must use HTTPS scheme to prevent credential leakage. Got scheme: '$scheme' in URL: $url"
            )
        }

        // 2. 호스트 검증: 반드시 maven.pkg.github.com만 허용
        val host = uri.host?.lowercase()
        if (host != ALLOWED_HOST) {
            throw SecurityException(
                "GitHub Packages URL host must be '$ALLOWED_HOST' to prevent arbitrary token forwarding. Got host: '$host' in URL: $url"
            )
        }

        // 3. UserInfo 차단: user:pass@host 형태 금지
        if (uri.rawUserInfo != null || uri.userInfo != null) {
            throw SecurityException("GitHub Packages URL must not contain user credentials (userInfo): $url")
        }

        // 4. Query 문자열 차단: ?param=val 형태 금지
        if (uri.rawQuery != null || uri.query != null) {
            throw SecurityException("GitHub Packages URL must not contain query parameters: $url")
        }

        // 5. Fragment 차단: #hash 형태 금지
        if (uri.rawFragment != null || uri.fragment != null) {
            throw SecurityException("GitHub Packages URL must not contain fragment: $url")
        }

        // 6. 포트 검증: 기본 포트(443) 또는 미지정(-1)만 허용
        val port = uri.port
        if (port != -1 && port != 443) {
            throw SecurityException(
                "GitHub Packages URL must use standard HTTPS port (443 or default). Got port: $port in URL: $url"
            )
        }

        // 7. 저장소 경로(Path) 검증: 반드시 /dev-inho/backend-init만 허용
        val path = uri.path?.trimEnd('/') ?: ""
        if (path != ALLOWED_PATH_PREFIX) {
            throw SecurityException(
                "GitHub Packages repository path must be '$ALLOWED_PATH_PREFIX' to prevent publishing to untrusted repositories. Got path: '$path' in URL: $url"
            )
        }

        return uri
    }
}
