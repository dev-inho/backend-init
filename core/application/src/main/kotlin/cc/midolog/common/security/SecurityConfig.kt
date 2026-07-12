package cc.midolog.common.security

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
 * Application 자체 JWT 검증(defense-in-depth). Gateway를 우회해 8081에 직접
 * 접근하는 경로를 차단하기 위해, 기존 [JwtProvider]/jjwt를 재사용하는 커스텀
 * 인증 컨버터/필터를 WebFlux Security DSL에 연결한다.
 *
 * - `/actuator/health`, `POST /api/auth/token`: 익명 허용
 * - 그 외 /api 하위 전체 경로: Authorization Bearer JWT 인증 필수
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
) {

    /** 익명 허용 경로를 제외한 /api 하위 요청에 Bearer JWT 인증을 강제하는 필터 체인. */
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val authTokenEndpoint = ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/auth/token")
        val protectedApiMatcher = AndServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers("/api/**"),
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
                    .pathMatchers("/api/**").authenticated()
                    .anyExchange().permitAll()
            }
            .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    /** Authorization 헤더의 "Bearer &lt;token&gt;"을 추출해 미인증 Authentication으로 변환한다. */
    private fun bearerTokenAuthenticationConverter(): ServerAuthenticationConverter =
        ServerAuthenticationConverter { exchange ->
            Mono.justOrEmpty(exchange.request.headers.getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION))
                .filter { it.startsWith("Bearer ") }
                .map { header ->
                    val token = header.removePrefix("Bearer ").trim()
                    UsernamePasswordAuthenticationToken(token, token) as Authentication
                }
        }

    /** [JwtProvider]로 서명을 검증하고, subject를 principal로 하는 인증된 Authentication을 만든다. */
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
