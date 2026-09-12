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

/**
 * Spring WebFlux 기반의 반응형 데이터 스트리밍 동작을 보여주는 샘플 컨트롤러.
 *
 * 코루틴 [Flow]를 웹 응답으로 노출하는 패턴을 제공하며, 현재는 참조하는 비즈니스 호출부가 없어 docs/DEAD_CODE_CANDIDATES.md #5에 등재되어 있다.
 */
@RestController
@RequestMapping("/api/sample")
class SampleStreamController {

    /**
     * 일정 주기로 생성되는 샘플 데이터를 NDJSON(Newline Delimited JSON) 포맷의 스트림으로 클라이언트에 전달한다.
     *
     * 전체 결과를 한 번에 버퍼링하지 않고 줄바꿈 단위로 즉시 전송하기 위해 NDJSON 미디어 타입을 사용하며,
     * 클라이언트의 소비 속도에 맞춰 배압(backpressure)을 비동기적으로 제어하기 위해 코루틴 [Flow]를 반환한다.
     */
    @GetMapping("/stream", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun stream(): Flow<SampleResponse> = flow {
        repeat(3) { i ->
            delay(100)
            emit(SampleResponse.from(Sample(id = "stream_$i", name = "item-$i")))
        }
    }
}
