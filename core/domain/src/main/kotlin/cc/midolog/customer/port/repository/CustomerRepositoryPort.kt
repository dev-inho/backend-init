package cc.midolog.customer.port.repository

/**
 * 고객 확장 엔티티 영속성을 위한 범용 도메인 저장소 포트.
 *
 * 엔티티 타입과 식별자 타입을 제네릭 매개변수로 취하며, 하부 영속 기술에
 * 의존하지 않고 저장, 단건 조회, 삭제 연산을 비동기 코루틴으로 제공한다.
 * 블로킹 저장소 호출을 격리하여 이벤트루프 스레드를 차단하지 않도록 구현해야 한다.
 */
interface CustomerRepositoryPort<T : Any, ID : Any> {

    /**
     * 식별자로 엔티티를 단건 조회한다.
     * 일치하는 데이터가 없으면 null을 반환한다.
     */
    suspend fun findById(id: ID): T?

    /**
     * 엔티티를 저장한다.
     * 동일 식별자가 존재하지 않으면 삽입하고 이미 존재하면 갱신하는 upsert 동작을 보장한다.
     */
    suspend fun save(entity: T): T

    /**
     * 식별자로 엔티티를 삭제한다.
     * 삭제 대상 행이 존재하여 정상 삭제되면 true를, 존재하지 않으면 false를 반환한다.
     */
    suspend fun deleteById(id: ID): Boolean
}
