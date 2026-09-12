package cc.midolog.web.sample.dto

import cc.midolog.sample.model.Sample

data class SampleResponse(
    val id: String,
    val name: String,
) {
    companion object {
        fun from(sample: Sample): SampleResponse =
            SampleResponse(
                id = sample.id,
                name = sample.name,
            )
    }
}
