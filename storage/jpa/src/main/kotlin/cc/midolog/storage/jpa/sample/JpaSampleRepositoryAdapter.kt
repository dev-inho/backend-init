package cc.midolog.storage.jpa.sample

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionOperations

@Profile("jpa")
@Repository
class JpaSampleRepositoryAdapter(
    private val sampleJpaRepository: SampleJpaRepository,
    private val transactionOperations: TransactionOperations,
) : SampleRepositoryPort {

    override suspend fun findById(id: String): Sample? = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            sampleJpaRepository.findById(id)
                .map(SampleJpaMapper::toDomain)
                .orElse(null)
        }
    }

    override suspend fun save(sample: Sample): Sample = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            SampleJpaMapper.toDomain(
                sampleJpaRepository.save(SampleJpaMapper.toEntity(sample)),
            )
        } ?: error("JPA sample save transaction returned no result")
    }
}
