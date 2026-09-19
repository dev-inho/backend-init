package cc.midolog.buildlogic.publishing

import org.gradle.api.GradleException
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * 원격 GitHub Packages Maven 레지스트리에 아티팩트가 이미 존재하는지 선제적으로 검사하는 사전 검사기.
 *
 * 업로드를 시작하기 전에 모든 publishing 모듈 및 BOM의 해당 버전 POM 파일 존재를 조회하여,
 * 하나라도 이미 존재하면 어떠한 아티팩트도 업로드하지 않고 사전에 실패 처리한다.
 * HTTP 401/403 권한 오류나 네트워크 장애를 404(미존재)로 취급하지 않고 안전하게 실패 처리한다.
 * 리다이렉트 발생 시 자동 추적을 차단하여 토큰 유출을 방지한다.
 */
class RemoteArtifactPreflightChecker(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    private val allowInsecureTestUrl: Boolean = false,
) {

    /**
     * 검사 대상 아티팩트 정보 (groupId, artifactId, version).
     */
    data class TargetArtifact(
        val groupId: String,
        val artifactId: String,
        val version: String,
    ) {
        /**
         * 저장소 루트 기준 POM 파일의 상대 경로를 반환한다.
         */
        fun toPomRelativePath(): String {
            val groupPath = groupId.replace('.', '/')
            return "$groupPath/$artifactId/$version/$artifactId-$version.pom"
        }
    }

    /**
     * 대상 아티팩트 목록 전체에 대해 원격 저장소에 이미 존재하는지 선제적으로 검사한다.
     * 아티팩트가 하나라도 이미 존재하거나, 네트워크/인증 에러 발생 시 GradleException을 발생시킨다.
     */
    fun checkAll(
        baseUrl: String,
        username: String,
        token: String,
        artifacts: List<TargetArtifact>,
        logger: (String) -> Unit = {},
    ) {
        val validatedUri = GithubPackagesUrlValidator.validate(baseUrl, allowInsecureTestUrl)
        val normalizedBaseUrl = validatedUri.toString().trimEnd('/')

        val credentials = "$username:$token"
        val basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))

        for (artifact in artifacts) {
            val pomUrl = "$normalizedBaseUrl/${artifact.toPomRelativePath()}"
            val pomUri = URI.create(pomUrl)

            logger("Preflight checking remote artifact existence: ${artifact.artifactId}:${artifact.version} at $pomUrl")

            val request = HttpRequest.newBuilder()
                .uri(pomUri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", basicAuth)
                .header("User-Agent", "backend-init-publishing-preflight")
                .build()

            val response: HttpResponse<Void> = try {
                httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            } catch (e: IOException) {
                throw GradleException(
                    "Failed to check remote artifact existence for ${artifact.artifactId}:${artifact.version} due to network error: ${e.message}. Aborting publish to prevent partial upload."
                )
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw GradleException(
                    "Interrupted while checking remote artifact existence for ${artifact.artifactId}:${artifact.version}. Aborting publish."
                )
            }

            val statusCode = response.statusCode()

            // 1. 이미 존재하는 경우 (200 OK) -> 버전 중복으로 업로드 전 선제적 차단
            if (statusCode in 200..299) {
                throw GradleException(
                    "Remote release artifact already exists: ${artifact.groupId}:${artifact.artifactId}:${artifact.version} (HTTP $statusCode at $pomUrl). " +
                        "Overwriting existing releases is strictly prohibited. Preflight check failed before any artifacts were uploaded."
                )
            }

            // 2. 리다이렉트 응답 (3xx) -> 토큰 유출 방지를 위해 차단
            if (statusCode in 300..399) {
                throw SecurityException(
                    "Unexpected redirect (HTTP $statusCode) received from $pomUrl during preflight check. Redirects are blocked to prevent credential leakage."
                )
            }

            // 3. 권한/인증 오류 (401, 403) -> 404로 오인하지 않고 즉시 실패
            if (statusCode == 401 || statusCode == 403) {
                throw GradleException(
                    "Failed to verify remote artifact existence for ${artifact.artifactId}:${artifact.version} due to authentication/authorization failure (HTTP $statusCode at $pomUrl). " +
                        "Please verify your GitHub Packages credentials and permissions. Aborting publish."
                )
            }

            // 4. 미존재 확인 (404 Not Found) -> 정상 게시 가능
            if (statusCode == 404) {
                continue
            }

            // 5. 기타 모든 오류 코드 (5xx 등) -> 404로 취급하지 않고 실패
            throw GradleException(
                "Failed to verify remote artifact existence for ${artifact.artifactId}:${artifact.version} due to unexpected HTTP status $statusCode at $pomUrl. Aborting publish."
            )
        }

        logger("Preflight check passed: all ${artifacts.size} target artifacts are not present on remote repository.")
    }
}
