package cc.midolog.storage.mybatis.config

import cc.midolog.storage.mybatis.autoconfigure.MyBatisStorageAutoConfiguration
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MyBatisStorageConfigTest {

    @Test
    fun `auto configuration loads`() {
        val config = MyBatisStorageAutoConfiguration()
        assertNotNull(config)
    }
}
