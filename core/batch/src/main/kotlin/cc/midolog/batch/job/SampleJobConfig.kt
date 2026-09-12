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
 * sample 테이블의 데이터 건수를 집계하여 로깅하는 단일 스텝 배치 잡 설정.
 *
 * application.yml 설정(`spring.batch.jdbc.initialize-schema: always`)에 따라 기동 시 배치 메타데이터 테이블이 자동 구성되며,
 * `spring.batch.job.enabled: true` 설정과 연계되어 [cc.midolog.BatchApplication] 구동 직후 이 잡이 자동 실행된다.
 * 대용량 데이터 분할 가공이 불필요한 단순 통계 목적이므로 청크(chunk) 지향 프로세싱 대신 경량의 단일 Tasklet 구조를 채택한다.
 */
@Configuration
class SampleJobConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * sample 테이블의 전체 행 수를 카운트하여 로그에 기록하는 Tasklet 스텝을 정의한다.
     *
     * 도메인 모델 로딩이나 유효성 검증을 거치지 않는 순수 통계 쿼리(`SELECT count(*) FROM sample`)이므로, 도메인 포트([cc.midolog.sample.port.repository.SampleRepositoryPort]) 계층을 우회하고 [JdbcTemplate]을 직접 호출하여 불필요한 객체 변환 오버헤드를 배제한다.
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
     * [sampleStep]을 유일한 실행 단계로 등록하여 배치 작업 [Job] 인스턴스를 빌드한다.
     *
     * 별도의 조건부 분기나 다단계 플로우가 필요하지 않은 일회성 집계 작업이므로, 추가적인 스텝 연결 없이 단일 스텝 구성으로 단순하게 완결한다.
     */
    @Bean
    fun sampleJob(jobRepository: JobRepository, sampleStep: Step): Job = JobBuilder("sampleJob", jobRepository)
        .start(sampleStep)
        .build()
}
