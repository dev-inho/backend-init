package cc.midolog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 백엔드 애플리케이션 서버 구동을 담당하는 메인 스프링 부트 설정 클래스.
 *
 * `scanBasePackages = ["cc.midolog"]` 설정을 통해 애플리케이션 하위 패키지뿐만 아니라 support:web 모듈에 위치한 공통 웹 필터 및 전역 예외 핸들러 컴포넌트를 함께 스캔하여 암묵적으로 빈으로 등록한다.
 */
@SpringBootApplication(scanBasePackages = ["cc.midolog"])
class ApplicationServer

fun main(args: Array<String>) {
    runApplication<ApplicationServer>(*args)
}
