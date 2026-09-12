package cc.midolog.web.user.dto

import cc.midolog.user.model.User

/**
 * 사용자 정보 조회 및 생성 결과의 외부 응답 계약을 정의하는 DTO.
 *
 * 내부 도메인 엔티티([User])와 분리하여 API 응답 계약을 안정적으로 고정한다.
 * 도메인 모델에 내부 상태 필드가 추가되더라도 외부에 무분별하게 노출되지 않도록 격리하는 역할을 한다.
 */
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
) {
    companion object {
        /**
         * [User] 도메인 모델의 id, email, displayName 필드를 동일한 이름과 값으로 1:1 매핑하여 응답 DTO를 생성한다.
         */
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
            )
    }
}
