package cc.midolog.storage.mybatis.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mybatis.spring.annotation.MapperScan

class MyBatisStorageConfigTest {

    @Test
    fun `mapper scan includes sample and user packages`() {
        val mapperScan = MyBatisStorageConfig::class.java.getAnnotation(MapperScan::class.java)

        assertTrue("cc.midolog.storage.mybatis.sample" in mapperScan.basePackages)
        assertTrue("cc.midolog.storage.mybatis.user" in mapperScan.basePackages)
    }
}
