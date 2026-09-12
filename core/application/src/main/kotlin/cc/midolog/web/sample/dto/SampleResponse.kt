package cc.midolog.web.sample.dto

import cc.midolog.sample.model.Sample

/**
 * 샘플 조회 및 생성 결과의 외부 응답 계약을 정의하는 DTO.
 *
 * 내부 도메인 모델([Sample])과 분리하여 외부 클라이언트와의 응답 규격을 고정한다.
 * 향후 도메인 엔티티에 내부 로직용 필드가 추가되거나 변경되더라도 API 스펙이 영향을 받지 않도록 보호한다.
 */
data class SampleResponse(
    val id: String,
    val name: String,
) {
    companion object {
        /**
         * [Sample] 도메인 모델의 식별자(id)와 이름(name)을 1:1로 매핑하여 응답 DTO를 생성한다.
         */
        fun from(sample: Sample): SampleResponse =
            SampleResponse(
                id = sample.id,
                name = sample.name,
            )
    }
}
