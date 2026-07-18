package cc.midolog.storage.jpa.sample

import cc.midolog.sample.model.ScalarSampleCode

object ScalarSampleCodeJpaConverter {
    fun toStorage(code: ScalarSampleCode): String = code.value

    fun toDomain(value: String): ScalarSampleCode = ScalarSampleCode(value)
}
