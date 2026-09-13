package cc.midolog.storage.mybatis.file

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.mybatis.dynamic.sql.util.mybatis3.CommonSelectMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonUpdateMapper
import java.time.Instant

/**
 * 파일 메타데이터 테이블(file_meta) 조작을 위한 MyBatis 매퍼 인터페이스.
 *
 * 조회 동작은 CommonSelectMapper, 상태 변경은 CommonUpdateMapper를 상속하여 MyBatis Dynamic SQL을 통해 수행하며,
 * Upsert 동작은 PostgreSQL과 H2의 원자적 방언 지원을 위해 매퍼 XML의 databaseId 분기 구문으로 실행한다.
 */
@Mapper
interface FileMetaMapper : CommonSelectMapper, CommonUpdateMapper {

    /**
     * 파일 메타데이터를 저장하거나 이미 존재하는 경우 최신 메타데이터로 갱신한다.
     * 방언별 원자적 저장을 위해 매퍼 XML의 databaseId 분기 구문을 실행한다.
     */
    fun upsert(
        @Param("id") id: String,
        @Param("ownerId") ownerId: String,
        @Param("storageKey") storageKey: String,
        @Param("sizeBytes") sizeBytes: Long?,
        @Param("contentType") contentType: String?,
        @Param("checksum") checksum: String?,
        @Param("status") status: String,
        @Param("createdAt") createdAt: Instant,
        @Param("updatedAt") updatedAt: Instant,
    ): Int
}
