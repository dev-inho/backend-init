package cc.midolog.examples.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 로컬 Maven 아티팩트 저장소와 BOM(`cc.midolog:backend-init-bom`)을 통해 의존성을 해석하고
 * 프레임워크 계약을 증명하는 독립 소비자 예제 애플리케이션.
 */
@SpringBootApplication(scanBasePackages = ["cc.midolog.examples.consumer"])
class ArtifactConsumerApplication

fun main(args: Array<String>) {
    runApplication<ArtifactConsumerApplication>(*args)
}
