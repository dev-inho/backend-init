package cc.midolog.web.sample

import cc.midolog.business.service.SampleService
import cc.midolog.sample.model.Sample
import cc.midolog.web.exception.ApiException
import cc.midolog.web.response.ApiResponse
import cc.midolog.web.sample.dto.CreateSampleRequest
import cc.midolog.web.sample.dto.SampleResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * `/api/sample` 경로 접두를 가지는 샘플 리소스 웹 엔드포인트.
 *
 * 내부 도메인 모델([Sample])이 외부에 노출되지 않도록 모든 응답을 표준 봉투 [ApiResponse]와 전용 응답 DTO [SampleResponse]로 감싸서 반환한다.
 * 도메인 모델 누출 금지 계약은 [cc.midolog.web.ControllerResponseTypeTest] 가드 테스트로 강제된다.
 */
@RestController
@RequestMapping("/api/sample")
class SampleController(
    private val sampleService: SampleService,
) {
    /** 애플리케이션 정상 동작 여부를 확인하기 위한 헬스체크용 샘플 엔드포인트. */
    @GetMapping("/ping")
    fun ping(): ApiResponse<Map<String, String>> = ApiResponse.ok(mapOf("status" to "ok"))

    /**
     * 식별자로 샘플 데이터를 조회하여 200 OK와 [SampleResponse]를 반환한다.
     *
     * 대상 샘플이 존재하지 않을 경우 [ApiException.notFound] 예외를 발생시켜 HTTP 404 상태 코드로 응답한다.
     */
    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: String): ApiResponse<SampleResponse> {
        val sample = sampleService.findById(id)
            ?: throw ApiException.notFound("sample not found: $id")
        return ApiResponse.ok(SampleResponse.from(sample))
    }

    /**
     * 신규 샘플 데이터를 등록하고 201 Created와 [SampleResponse]를 반환한다.
     *
     * 요청 본문([CreateSampleRequest])은 [@Valid]에 의해 필드 제약을 검증받으며, 유효하지 않은 입력은 400 Bad Request 에러로 거부된다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@Valid @RequestBody request: CreateSampleRequest): ApiResponse<SampleResponse> {
        val saved = sampleService.save(Sample(id = request.id, name = request.name))
        return ApiResponse.ok(SampleResponse.from(saved))
    }

    /** 요청 본문 문자열을 그대로 반환하는 에코 테스트용 샘플 엔드포인트. */
    @PostMapping("/echo")
    fun echo(@RequestBody body: String): ApiResponse<String> = ApiResponse.ok(body)
}
