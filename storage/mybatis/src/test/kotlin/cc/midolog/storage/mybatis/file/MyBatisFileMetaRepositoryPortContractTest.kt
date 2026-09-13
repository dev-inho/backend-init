package cc.midolog.storage.mybatis.file

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.repository.FileMetaRepositoryPortContract
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@MybatisTest
@org.springframework.context.annotation.Import(cc.midolog.storage.mybatis.TestDatabaseIdProviderConfig::class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_filemeta;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
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
        // GUARD: Adapter is instantiated without Clock (current main behavior)
        return MyBatisFileMetaRepositoryAdapter(mapper, clock())
    }

    override fun clock(): Clock {
        return testClock
    }
}
