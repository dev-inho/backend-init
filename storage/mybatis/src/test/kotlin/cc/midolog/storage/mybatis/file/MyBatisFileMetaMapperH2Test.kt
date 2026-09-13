package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileStatus
import cc.midolog.storage.mybatis.autoconfigure.MyBatisStorageAutoConfiguration
import cc.midolog.storage.mybatis.file.FileMetaDynamicSqlSupport.fileMeta
import org.junit.jupiter.api.Test
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.select
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.update
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@MybatisTest
@Import(MyBatisStorageAutoConfiguration::class)
@TestPropertySource(properties = [
    "storage.persistence.provider=mybatis",
    "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
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
    fun `dynamic sql queries and dialect upsert work properly on H2`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        // findExpiredPending via Dynamic SQL
        val findExpiredStatement = select(
            FileMetaDynamicSqlSupport.id,
            FileMetaDynamicSqlSupport.ownerId,
            FileMetaDynamicSqlSupport.status,
            FileMetaDynamicSqlSupport.updatedAt,
        ) {
            from(fileMeta)
            where {
                FileMetaDynamicSqlSupport.status isEqualTo FileStatus.PENDING
                and { FileMetaDynamicSqlSupport.updatedAt isLessThan now.minusSeconds(50) }
            }
            orderBy(FileMetaDynamicSqlSupport.updatedAt)
            limit(1)
        }
        val expired = mapper.selectManyMappedRows(findExpiredStatement)
        assertEquals(1, expired.size)
        assertEquals("f1", expired[0]["id"] ?: expired[0]["ID"])

        // updateStatus via Dynamic SQL
        val updateStatement = update(fileMeta) {
            set(FileMetaDynamicSqlSupport.status).equalTo(FileStatus.READY)
            set(FileMetaDynamicSqlSupport.updatedAt).equalTo(now)
            where { FileMetaDynamicSqlSupport.id isEqualTo "f1" }
        }
        val updated = mapper.update(updateStatement)
        assertEquals(1, updated)

        // selectById via Dynamic SQL
        val selectByIdStatement = select(
            FileMetaDynamicSqlSupport.id,
            FileMetaDynamicSqlSupport.status,
            FileMetaDynamicSqlSupport.updatedAt,
        ) {
            from(fileMeta)
            where { FileMetaDynamicSqlSupport.id isEqualTo "f1" }
        }
        val row = mapper.selectOneMappedRow(selectByIdStatement)
        assertNotNull(row)
        assertEquals(FileStatus.READY.name, (row["status"] ?: row["STATUS"]) as String)

        // upsert via H2 databaseId dialect
        val upsertResult = mapper.upsert(
            id = "f3",
            ownerId = "o3",
            storageKey = "k3",
            sizeBytes = 200L,
            contentType = "text/plain",
            checksum = "abc",
            status = FileStatus.READY.name,
            createdAt = now,
            updatedAt = now,
        )
        assertEquals(1, upsertResult)
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.mybatis.spring.annotation.MapperScan("cc.midolog.storage.mybatis.file")
    class MyBatisTestConfig
}
