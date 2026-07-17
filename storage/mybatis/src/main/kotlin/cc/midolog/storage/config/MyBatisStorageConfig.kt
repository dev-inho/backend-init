package cc.midolog.storage.config

import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("mybatis")
@Configuration
@MapperScan(
    basePackages = [
        "cc.midolog.storage.sample",
        "cc.midolog.storage.user",
    ],
)
class MyBatisStorageConfig
