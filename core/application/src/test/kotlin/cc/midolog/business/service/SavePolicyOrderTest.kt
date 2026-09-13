package cc.midolog.business.service

import cc.midolog.sample.model.Sample
import cc.midolog.sample.policy.SampleSavePolicy
import cc.midolog.sample.port.cache.SampleCachePort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.model.User
import cc.midolog.user.policy.UserSavePolicy
import cc.midolog.user.port.repository.UserRepositoryPort
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SavePolicyOrderTest {

    private val sampleRepo = object : SampleRepositoryPort {
        override suspend fun save(sample: Sample): Sample = sample
        override suspend fun findById(id: String): Sample? = null
    }

    private val sampleCache = object : SampleCachePort {
        override suspend fun get(id: String): Sample? = null
        override suspend fun put(sample: Sample) {}
    }

    private val userRepo = object : UserRepositoryPort {
        override suspend fun save(user: User): User = user
        override suspend fun findById(id: String): User? = null
    }

    @Test
    fun `SampleSavePolicy 복수 등록 시 등록 순서와 관계없이 order 오름차순으로 순차 적용되어야 한다`(): Unit = runBlocking {
        // order 20: 접미사 추가
        val suffixPolicy = object : SampleSavePolicy {
            override val order: Int get() = 20
            override suspend fun beforeSave(sample: Sample): Sample =
                sample.copy(name = "${sample.name}_suffix")
        }

        // order 10: 대문자 변환
        val upperPolicy = object : SampleSavePolicy {
            override val order: Int get() = 10
            override suspend fun beforeSave(sample: Sample): Sample =
                sample.copy(name = sample.name.uppercase())
        }

        // 등록 순서를 역순(order 20 먼저, order 10 나중)으로 전달
        val service = SampleService(sampleRepo, sampleCache, listOf(suffixPolicy, upperPolicy))
        val result = service.save(Sample("1", "hello"))

        // order 10 (HELLO) -> order 20 (HELLO_suffix) 순서로 적용되어야 함
        assertThat(result.name).isEqualTo("HELLO_suffix")
    }

    @Test
    fun `UserSavePolicy 복수 등록 시 등록 순서와 관계없이 order 오름차순으로 순차 적용되어야 한다`(): Unit = runBlocking {
        val suffixPolicy = object : UserSavePolicy {
            override val order: Int get() = 20
            override suspend fun beforeSave(user: User): User =
                user.copy(displayName = "${user.displayName}_suffix")
        }

        val upperPolicy = object : UserSavePolicy {
            override val order: Int get() = 10
            override suspend fun beforeSave(user: User): User =
                user.copy(displayName = user.displayName.uppercase())
        }

        val service = UserService(userRepo, listOf(suffixPolicy, upperPolicy))
        val result = service.save(User("1", "user@test.com", "world"))

        assertThat(result.displayName).isEqualTo("WORLD_suffix")
    }
}
