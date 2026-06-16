package cc.midolog.batch.job

import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * 샘플 배치 — sample 테이블 행 수를 집계해 로깅하는 단일 Step Job.
 */
@Configuration
class SampleJobConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * sample 테이블의 행 수를 쿼리하고 로깅하는 Tasklet Step.
     */
    @Bean
    fun sampleStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        jdbcTemplate: JdbcTemplate,
    ): Step = StepBuilder("sampleStep", jobRepository)
        .tasklet({ _, _ ->
            val count = jdbcTemplate.queryForObject("SELECT count(*) FROM sample", Int::class.java)
            log.info("[sampleStep] sample 테이블 행 수 = {}", count)
            RepeatStatus.FINISHED
        }, transactionManager)
        .build()

    /**
     * sampleStep을 실행하는 배치 Job.
     */
    @Bean
    fun sampleJob(jobRepository: JobRepository, sampleStep: Step): Job = JobBuilder("sampleJob", jobRepository)
        .start(sampleStep)
        .build()
}
