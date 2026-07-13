package cc.midolog.web.sample

import cc.midolog.business.service.SampleService
import cc.midolog.sample.model.Sample
import cc.midolog.web.exception.ApiException
import cc.midolog.web.response.ApiResponse
import cc.midolog.web.sample.dto.CreateSampleRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sample")
class SampleController(
    private val sampleService: SampleService,
) {
    @GetMapping("/ping")
    fun ping(): ApiResponse<Map<String, String>> = ApiResponse.ok(mapOf("status" to "ok"))

    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: String): ApiResponse<Sample> {
        val sample = sampleService.findById(id)
            ?: throw ApiException.notFound("sample not found: $id")
        return ApiResponse.ok(sample)
    }

    @PostMapping
    suspend fun create(@Valid @RequestBody request: CreateSampleRequest): ApiResponse<Sample> {
        val saved = sampleService.save(Sample(id = request.id, name = request.name))
        return ApiResponse.ok(saved)
    }

    @PostMapping("/echo")
    fun echo(@RequestBody body: String): ApiResponse<String> = ApiResponse.ok(body)
}
