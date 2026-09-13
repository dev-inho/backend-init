package cc.midolog.examples.minimal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


/**
 * 프레임워크 저장소 스타터 모듈의 자동 구성 및 확장 계약을 증명하는 최소 소비자 예제 애플리케이션.
 *
 * 저장소 어댑터 패키지(cc.midolog.storage.*)를 호스트 컴포넌트 스캔으로 긁어오지 않고
 * 오직 스프링 부트 표준 AutoConfiguration 메커니즘과 storage.persistence.provider 프로퍼티로만
 * 올바른 저장소 포트 빈이 등록됨을 보장하기 위해 베이스 스캔 패키지를 자신(cc.midolog.examples.minimal)으로 엄격히 한정한다.
 */
@SpringBootApplication(scanBasePackages = ["cc.midolog.examples.minimal"])
class MinimalApplication

fun main(args: Array<String>) {
    runApplication<MinimalApplication>(*args)
}
