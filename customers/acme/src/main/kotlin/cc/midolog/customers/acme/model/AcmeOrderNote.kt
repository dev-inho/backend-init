package cc.midolog.customers.acme.model

/**
 * Acme 고객 전용 주문 메모를 나타내는 순수 도메인 확장 엔티티.
 *
 * 영속성 프레임워크에 결합되지 않은 순수 도메인 모델이며,
 * 주문 메모 식별자와 고객 식별자, 메모 본문, 생성 시각을 보관한다.
 */
data class AcmeOrderNote(
    val id: String,
    val customerId: String,
    val note: String,
    val createdAt: java.time.Instant,
)
