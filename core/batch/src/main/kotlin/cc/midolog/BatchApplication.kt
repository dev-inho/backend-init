package cc.midolog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Batch 배치 작업을 구동하기 위한 메인 스프링 부트 애플리케이션 설정 클래스.
 *
 * `scanBasePackages = ["cc.midolog"]`를 기반으로 모듈 내의 배치 잡 구성 빈과 공통 인프라 컴포넌트를 스캔하여 부트스트랩을 수행한다.
 */
@SpringBootApplication(scanBasePackages = ["cc.midolog"])
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
