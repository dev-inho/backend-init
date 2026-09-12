package cc.midolog.storage.mybatis

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration

@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackages = ["cc.midolog.storage.mybatis"])
class MyBatisTestApplication
