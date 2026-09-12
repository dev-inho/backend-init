package cc.midolog.storage.mybatis.file

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.repository.FileMetaRepositoryPortContract
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace

@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_filemeta;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "mybatis.mapper-locations=classpath:mapper/**/*.xml"
])
@Sql(statements = [
    "DROP TABLE IF EXISTS file_meta;",
    "CREATE TABLE file_meta (id VARCHAR(255) PRIMARY KEY, owner_id VARCHAR(255) NOT NULL, storage_key VARCHAR(255) NOT NULL, status VARCHAR(20) NOT NULL, size_bytes BIGINT, content_type VARCHAR(255), checksum VARCHAR(255), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL);"
])
class MyBatisFileMetaRepositoryPortContractTest : FileMetaRepositoryPortContract() {

    @Autowired
    private lateinit var mapper: FileMetaMapper

    private var testClock: Clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"))

    override fun port(): FileMetaRepositoryPort {
        return MyBatisFileMetaRepositoryAdapter(mapper, testClock)
    }

    override fun clock(): Clock {
        return testClock
    }
    
    @org.springframework.boot.SpringBootConfiguration
    @org.mybatis.spring.annotation.MapperScan("cc.midolog.storage.mybatis.file")
    class MyBatisTestConfig {
        @org.springframework.context.annotation.Bean
        fun databaseIdProvider(): org.apache.ibatis.mapping.DatabaseIdProvider {
            val provider = org.apache.ibatis.mapping.VendorDatabaseIdProvider()
            val properties = java.util.Properties()
            properties.setProperty("H2", "h2")
            provider.setProperties(properties)
            return provider
        }
    }

    @Autowired
    private lateinit var dataSource: javax.sql.DataSource

    @Test
    fun printUrl() {
        println("DB URL: " + dataSource.connection.metaData.url)
    }

    @Test
    fun checkMode() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='MODE'").use { rs ->
                    if (rs.next()) {
                        println("H2 MODE: " + rs.getString(1))
                    }
                }
            }
        }
    }

}
