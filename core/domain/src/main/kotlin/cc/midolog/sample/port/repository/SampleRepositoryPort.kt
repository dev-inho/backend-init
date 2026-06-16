package cc.midolog.sample.port.repository

import cc.midolog.sample.model.Sample

/** Repository 출력 포트(코루틴 기반). storage 어댑터가 구현한다. */
interface SampleRepositoryPort {
    suspend fun findById(id: String): Sample?
    suspend fun save(sample: Sample): Sample
}
