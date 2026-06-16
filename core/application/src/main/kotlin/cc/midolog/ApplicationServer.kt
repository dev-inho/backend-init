package cc.midolog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main Spring Boot application server entry point.
 */
@SpringBootApplication(scanBasePackages = ["cc.midolog"])
class ApplicationServer

/**
 * Starts the Spring Boot application.
 *
 * @param args command-line arguments
 */
fun main(args: Array<String>) {
    runApplication<ApplicationServer>(*args)
}
