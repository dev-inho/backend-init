package cc.midolog.web.sample

import cc.midolog.sample.model.Sample
import cc.midolog.web.sample.dto.SampleResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 코루틴 Flow 스트리밍 예시 — WebFlux는 suspend/Flow 반환을 지원한다. */
@RestController
@RequestMapping("/api/sample")
class SampleStreamController {

    @GetMapping("/stream", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun stream(): Flow<SampleResponse> = flow {
        repeat(3) { i ->
            delay(100)
            emit(SampleResponse.from(Sample(id = "stream_$i", name = "item-$i")))
        }
    }
}
