package cc.midolog.storage.mybatis.sample

import org.apache.ibatis.annotations.Param

/**
 * 샘플 테이블(sample) 조작을 위한 MyBatis 매퍼 인터페이스.
 *
 * 조회의 경우 별도 DTO 없이 Map<String, Any?>으로 결과를 반환한다.
 * MyBatis에서 resultType="map"을 사용할 때는 application.yml의 map-underscore-to-camel-case
 * 설정이 Map 키 변환에 적용되지 않으므로, 컬럼명을 키로 직접 매핑하여 도메인 모델을 생성한다.
 */
interface SampleMapper {
    /**
     * 식별자로 샘플 행을 단건 조회한다.
     * 결과가 없으면 null을 반환하며, 존재할 경우 컬럼명을 키로 하는 Map을 반환한다.
     */
    fun selectById(@Param("id") id: String): Map<String, Any?>?

    /**
     * 샘플 데이터를 삽입하거나 이미 존재하면 이름을 갱신(ON CONFLICT DO UPDATE)한다.
     * 단일 행 갱신을 보장하기 위해 반환값으로 영향받은 행 수 1을 요구한다.
     */
    fun upsert(
        @Param("id") id: String,
        @Param("name") name: String,
    ): Int
}
