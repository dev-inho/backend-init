package cc.midolog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["cc.midolog"])
class BatchApplication

/**
 * Entry point for the batch application.
 */
fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
