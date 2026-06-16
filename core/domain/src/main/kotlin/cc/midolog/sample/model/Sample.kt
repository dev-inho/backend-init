package cc.midolog.sample.model

/** 샘플 도메인 모델 — 실제 도메인 추가 시 이 패턴(model/port)을 따른다. */
data class Sample(
    val id: String,
    val name: String,
)
