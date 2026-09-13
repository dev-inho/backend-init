package cc.midolog.examples.minimal.controller

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.web.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateSampleRequest(
    val id: String,
    val name: String,
)

data class SampleResponse(
    val id: String,
    val name: String,
) {
    companion object {
        fun from(sample: Sample): SampleResponse = SampleResponse(
            id = sample.id,
            name = sample.name,
        )
    }
}

/**
 * 최소 소비자 예제 애플리케이션의 샘플 리소스 웹 컨트롤러.
 *
 * 외부 인프라 계층의 구현체나 ORM 기술(JPA, MyBatis)에 직접 결합되지 않고
 * 순수 도메인 포트 [SampleRepositoryPort]와 도메인 모델 [Sample]만을 주입받아
 * 샘플 엔티티의 저장 및 조회 유저 플로우를 외부에 노출한다.
 */
@RestController
@RequestMapping("/samples")
class MinimalSampleController(
    @org.springframework.context.annotation.Lazy private val sampleRepositoryPort: SampleRepositoryPort,
) {

    /**
     * 신규 샘플 데이터를 저장하고 200 OK와 [SampleResponse]를 반환한다.
     */
    @PostMapping
    suspend fun save(@RequestBody request: CreateSampleRequest): ApiResponse<SampleResponse> {
        val saved = sampleRepositoryPort.save(Sample(id = request.id, name = request.name))
        return ApiResponse.ok(SampleResponse.from(saved))
    }

    /**
     * 식별자로 샘플 데이터를 조회하여 존재하면 200 OK와 [SampleResponse]를 반환하고, 없으면 404 Not Found를 응답한다.
     */
    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: String): ResponseEntity<ApiResponse<SampleResponse>> {
        val found = sampleRepositoryPort.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ApiResponse.ok(SampleResponse.from(found)))
    }
}
