package cc.midolog.storage.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mybatis.spring.annotation.MapperScan

class MyBatisStorageConfigTest {

    @Test
    fun `mapper scan includes sample and user packages`() {
        val mapperScan = MyBatisStorageConfig::class.java.getAnnotation(MapperScan::class.java)

        assertTrue("cc.midolog.storage.sample" in mapperScan.basePackages)
        assertTrue("cc.midolog.storage.user" in mapperScan.basePackages)
    }
}
