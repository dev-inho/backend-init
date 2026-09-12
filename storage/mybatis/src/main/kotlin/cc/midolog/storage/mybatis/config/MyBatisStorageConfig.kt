package cc.midolog.storage.mybatis.config

import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("mybatis")
@Configuration
@MapperScan(
    basePackages = [
        "cc.midolog.storage.mybatis.sample",
        "cc.midolog.storage.mybatis.user",
    ],
)
class MyBatisStorageConfig
