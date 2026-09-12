package cc.midolog.file.port.storage

import java.io.Closeable

/**
 * 프레임워크 비의존 파일 청크 스트리밍 라이터 인터페이스.
 *
 * 파일 다운로드 또는 서버 경유 스트리밍 시 네트워크 출력이나 대상 스토리지로 바이트를 순차 출력한다.
 * 자원 누수를 막기 위해 [Closeable]을 구현하며 완료 또는 중단 시 정상적으로 닫혀야 한다.
 */
interface ChunkWriter : Closeable {
    /**
     * 버퍼의 지정된 길이만큼 스토리지 또는 네트워크 응답 스트림으로 출력한다.
     */
    suspend fun writeChunk(buffer: ByteArray, length: Int)

    /**
     * 스트리밍 중 예외 발생 시 하위 자원을 정리하고 조기 중단을 전파한다.
     * 원인이 되는 예외가 있다면 인자로 함께 전달하여 정리 작업을 수행한다.
     */
    suspend fun cancel(cause: Throwable?)
}
