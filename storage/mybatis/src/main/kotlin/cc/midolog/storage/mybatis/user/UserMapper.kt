package cc.midolog.storage.mybatis.user

import org.apache.ibatis.annotations.Param

/**
 * 사용자 테이블(app_user) 조작을 위한 MyBatis 매퍼 인터페이스.
 *
 * 조회 시 Map<String, Any?> 형태로 결과를 반환한다. MyBatis의 resultType="map"은
 * 설정(map-underscore-to-camel-case)에 의한 카멜케이스 자동 변환이 지원되지 않으므로,
 * 매퍼 XML(UserMapper.xml:6)에서 display_name AS "displayName" 처럼 명시적 별칭을 지정해야 한다
 * (docs/DEAD_CODE_CANDIDATES.md #12 참조).
 */
interface UserMapper {
    /**
     * 식별자로 사용자 행을 단건 조회한다.
     * 일치하는 데이터가 없으면 null을 반환한다.
     */
    fun selectById(@Param("id") id: String): Map<String, Any?>?

    /**
     * 사용자 데이터를 삽입하거나 기존 행의 이메일과 표시명을 갱신(ON CONFLICT DO UPDATE)한다.
     * 단일 레코드 upsert 성공을 검증하기 위해 반환값으로 영향받은 행 수 1을 요구한다.
     */
    fun upsert(
        @Param("id") id: String,
        @Param("email") email: String,
        @Param("displayName") displayName: String,
    ): Int
}
