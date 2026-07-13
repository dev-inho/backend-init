package cc.midolog.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * 로그 이벤트의 포맷된 메시지를 MaskingSupport로 마스킹하는 logback 변환어(%mask).
 * local 콘솔 패턴과 prod logstash JSON 인코더의 pattern provider가 이 컨버터를 공유한다.
 */
class MaskingMessageConverter : ClassicConverter() {

    /**
     * 로그 이벤트의 포맷된 메시지에서 이메일/전화번호/카드번호 후보를 마스킹해 반환한다.
     */
    override fun convert(event: ILoggingEvent): String =
        MaskingSupport.maskText(event.formattedMessage)
}
