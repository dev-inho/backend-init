package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileStatus
import org.junit.jupiter.api.Test
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MybatisTest
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
])
class MyBatisFileMetaMapperH2Test {

    @Autowired
    private lateinit var mapper: FileMetaMapper

    @Test
    @Sql(statements = [
        "CREATE TABLE IF NOT EXISTS file_meta (id VARCHAR(255) PRIMARY KEY, owner_id VARCHAR(255) NOT NULL, storage_key VARCHAR(255) NOT NULL, status VARCHAR(20) NOT NULL, size_bytes BIGINT, content_type VARCHAR(255), checksum VARCHAR(255), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL);",
        "INSERT INTO file_meta (id, owner_id, storage_key, size_bytes, content_type, checksum, status, created_at, updated_at) VALUES ('f1', 'o1', 'k1', 100, 'text', 'hash', 'PENDING', CURRENT_TIMESTAMP - 100, CURRENT_TIMESTAMP - 100);",
        "INSERT INTO file_meta (id, owner_id, storage_key, size_bytes, content_type, checksum, status, created_at, updated_at) VALUES ('f2', 'o1', 'k2', 100, 'text', 'hash', 'PENDING', CURRENT_TIMESTAMP - 90, CURRENT_TIMESTAMP - 90);"
    ])
    fun `mapper queries work properly`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        // findExpiredPending
        val expired = mapper.findExpiredPending(FileStatus.PENDING.name, now.minusSeconds(50), 1)
        assertEquals(1, expired.size)
        assertEquals("f1", expired[0]["id"] ?: expired[0]["ID"])

        // updateStatus
        val updated = mapper.updateStatus("f1", FileStatus.READY.name, now)
        assertEquals(1, updated)

        // check finding again
        val row = mapper.selectById("f1")!!
        assertEquals(FileStatus.READY.name, row["status"] ?: row["STATUS"])
        val rowUpdatedAt = row["updatedAt"] ?: row["UPDATED_AT"]
        val actualInstant = when (rowUpdatedAt) {
            is java.time.LocalDateTime -> rowUpdatedAt.atZone(java.time.ZoneId.of("UTC")).toInstant()
            is java.sql.Timestamp -> rowUpdatedAt.toInstant()
            is Instant -> rowUpdatedAt
            else -> error("Unknown type: \${rowUpdatedAt?.javaClass}")
        }
        assertEquals(now, actualInstant, "updated_at should be correctly persisted and retrieved")
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.mybatis.spring.annotation.MapperScan("cc.midolog.storage.mybatis.file")
    class MyBatisTestConfig
}
