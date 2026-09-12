package cc.midolog.storage.mybatis.sample

import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPortContract
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace

@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_sample;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "mybatis.mapper-locations=classpath*:mapper-h2/**/*.xml"
])
@Sql(statements = [
    "DROP TABLE IF EXISTS sample;",
    "CREATE TABLE sample (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL);"
])
class MyBatisSampleRepositoryPortContractTest : SampleRepositoryPortContract() {

    @Autowired
    private lateinit var mapper: SampleMapper

    override fun port(): SampleRepositoryPort {
        return MyBatisSampleRepositoryAdapter(mapper)
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.mybatis.spring.annotation.MapperScan("cc.midolog.storage.mybatis.sample")
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
}
