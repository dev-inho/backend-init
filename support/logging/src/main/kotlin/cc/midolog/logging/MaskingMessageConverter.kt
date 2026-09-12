package cc.midolog.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * 로그 이벤트의 포맷된 메시지를 [MaskingSupport]를 통해 개인정보 마스킹 처리하는 Logback 변환어(`%mask`).
 *
 * `support/logging/src/main/resources/logback-spring.xml:3`의 conversionRule 설정을 통해 등록되어 동작한다.
 * 로컬 콘솔 패턴(`%mask`)과 운영 logstash JSON 인코더(메시지 필드의 `%mask`) 양쪽에서 로그 출력 직전
 * 이메일, 전화번호, 카드번호 후보를 마스킹하여 로그 저장소 평문 유출을 차단한다.
 */
class MaskingMessageConverter : ClassicConverter() {

    /**
     * 로그 이벤트의 포맷된 메시지에서 민감정보(이메일, 전화번호, 카드번호) 후보를 마스킹해 반환한다.
     */
    override fun convert(event: ILoggingEvent): String =
        MaskingSupport.maskText(event.formattedMessage)
}
