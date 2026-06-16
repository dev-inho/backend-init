package cc.midolog.storage.sample

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface SampleMapper {
    fun selectById(@Param("id") id: String): Map<String, Any?>?
}
