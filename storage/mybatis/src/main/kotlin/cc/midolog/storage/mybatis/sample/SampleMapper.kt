package cc.midolog.storage.mybatis.sample

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.mybatis.dynamic.sql.util.mybatis3.CommonSelectMapper

/**
 * 샘플 테이블(sample) 조작을 위한 MyBatis 매퍼 인터페이스.
 *
 * 조회 동작은 MyBatis Dynamic SQL의 CommonSelectMapper를 통해 타입 안전하게 수행하며,
 * Upsert 동작은 PostgreSQL과 H2의 원자적 방언 지원을 위해 매퍼 XML의 databaseId 분기 구문으로 실행한다.
 */
@Mapper
interface SampleMapper : CommonSelectMapper {

    /**
     * 샘플 데이터를 삽입하거나 이미 존재하는 경우 이름을 갱신한다.
     * 단일 행 갱신을 보장하기 위해 영향받은 행 수를 반환한다.
     */
    fun upsert(
        @Param("id") id: String,
        @Param("name") name: String,
    ): Int
}
