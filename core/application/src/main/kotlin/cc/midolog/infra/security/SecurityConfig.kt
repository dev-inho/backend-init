package cc.midolog.infra.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import reactor.core.publisher.Mono

/**
 * 애플리케이션 자체 JWT 검증을 구성하는 WebFlux 보안 설정.
 *
 * 게이트웨이를 우회하여 애플리케이션 포트에 직접 인입되는 비인가 접근을 심층 방어(defense-in-depth)하기 위해 [JwtProvider] 기반의 커스텀 인증 필터를 연결한다.
 * 엔드포인트 접근 제어 정책은 최소 권한 및 fail-closed 원칙을 철저히 따른다:
 * - `/actuator/health`, `POST /api/auth/token`: 익명 접근 허용
 * - /api, /internal/gateway 하위 전체 경로: Authorization Bearer JWT 인증 필수
 * - 명시적으로 허용되지 않은 나머지 모든 경로는 전부 차단한다(denyAll).
 *
 * 자격 증명이 설정되지 않거나 빈 값인 상태에서도 토큰이 발급되지 않고 차단되는 보안 특성은 [cc.midolog.infra.security.AuthFailClosedTest]에 의해 fail-closed 성질로 보장된다.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
) {

    /**
     * 익명 허용 경로를 제외한 /api, /internal/gateway 하위 요청에 Bearer JWT 토큰 인증을 강제하는 보안 필터 체인을 구성한다.
     *
     * CSRF, HTTP Basic, 폼 로그인, 로그아웃 등 브라우저 기반 세션 기능을 모두 비활성화하고, 인증 실패 시 401 Unauthorized 상태 코드를 즉시 응답하도록 진입점을 설정한다.
     */
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val authTokenEndpoint = ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/auth/token")
        val protectedApiMatcher = AndServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers("/api/**", "/internal/gateway/**"),
            NegatedServerWebExchangeMatcher(authTokenEndpoint),
        )

        val jwtAuthFilter = AuthenticationWebFilter(jwtReactiveAuthenticationManager()).apply {
            setServerAuthenticationConverter(bearerTokenAuthenticationConverter())
            setRequiresAuthenticationMatcher(protectedApiMatcher)
        }

        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .exceptionHandling { exceptionHandling ->
                exceptionHandling.authenticationEntryPoint { exchange, _ ->
                    Mono.fromRunnable { exchange.response.statusCode = HttpStatus.UNAUTHORIZED }
                }
            }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/actuator/health").permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                    .pathMatchers("/api/**", "/internal/gateway/**").authenticated()
                    .anyExchange().denyAll()
            }
            .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    /**
     * Authorization 헤더에서 "Bearer <token>" 형식의 토큰 문자열을 추출해 미인증 Authentication 토큰 객체로 변환한다.
     *
     * 헤더가 누락되었거나 Bearer 접두사로 시작하지 않는 경우 빈 Mono를 반환해 후속 인증 필터에서 인증 실패 처리되도록 한다.
     */
    private fun bearerTokenAuthenticationConverter(): ServerAuthenticationConverter =
        ServerAuthenticationConverter { exchange ->
            Mono.justOrEmpty(exchange.request.headers.getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION))
                .filter { it.startsWith("Bearer ") }
                .map { header ->
                    val token = header.removePrefix("Bearer ").trim()
                    UsernamePasswordAuthenticationToken(token, token) as Authentication
                }
        }

    /**
     * [JwtProvider]를 통해 JWT 서명을 검증하고 파싱된 subject를 주체(principal)로 가지는 인증 객체를 생성한다.
     *
     * 서명 불일치, 만료 등 파싱 중 발생하는 예외는 [BadCredentialsException]으로 감싸서 전달하여 인증 체인에서 401 처리가 수행되도록 유도한다.
     */
    private fun jwtReactiveAuthenticationManager(): ReactiveAuthenticationManager =
        ReactiveAuthenticationManager { authentication ->
            Mono.fromCallable {
                val token = authentication.credentials as String
                try {
                    val claims = jwtProvider.parse(token)
                    UsernamePasswordAuthenticationToken(claims.subject, token, AuthorityUtils.NO_AUTHORITIES) as Authentication
                } catch (e: Exception) {
                    throw BadCredentialsException("invalid JWT", e)
                }
            }
        }
}
