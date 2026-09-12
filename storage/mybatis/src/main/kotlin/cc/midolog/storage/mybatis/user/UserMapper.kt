package cc.midolog.storage.mybatis.user

import org.apache.ibatis.annotations.Param

interface UserMapper {
    fun selectById(@Param("id") id: String): Map<String, Any?>?

    fun upsert(
        @Param("id") id: String,
        @Param("email") email: String,
        @Param("displayName") displayName: String,
    ): Int
}
