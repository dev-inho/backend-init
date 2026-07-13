package cc.midolog

import cc.midolog.logging.ReactorMdc
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GatewayApplication

fun main(args: Array<String>) {
    ReactorMdc.enable()
    runApplication<GatewayApplication>(*args)
}
