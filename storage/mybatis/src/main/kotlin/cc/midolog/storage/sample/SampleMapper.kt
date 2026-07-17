package cc.midolog.storage.sample

import org.apache.ibatis.annotations.Param

interface SampleMapper {
    fun selectById(@Param("id") id: String): Map<String, Any?>?

    fun upsert(
        @Param("id") id: String,
        @Param("name") name: String,
    ): Int
}
