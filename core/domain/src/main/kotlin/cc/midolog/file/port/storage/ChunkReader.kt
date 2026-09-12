package cc.midolog.file.port.storage

import java.io.Closeable

/**
 * 프레임워크 비의존 파일 청크 스트리밍 리더 인터페이스.
 *
 * 파일 업로드 또는 서버 경유 스토리지 로드 시 청크 단위로 데이터를 순차적으로 읽는다.
 * 자원 누수를 막기 위해 [Closeable]을 구현하며 읽기 완료 또는 취소 시 닫혀야 한다.
 */
interface ChunkReader : Closeable {
    /**
     * 버퍼 크기만큼 스트림에서 데이터를 읽어 채우고, 실제로 읽은 바이트 수를 반환한다.
     * 스트림의 끝(EOF)에 도달하면 -1을 반환한다.
     */
    suspend fun readChunk(buffer: ByteArray): Int

    /**
     * 클라이언트 타임아웃이나 백프레셔 한계 도달 시 조기 자원 반환을 지시한다.
     * 취소를 유발한 예외가 있다면 원인으로 함께 전달하여 하위 자원을 정리한다.
     */
    suspend fun cancel(cause: Throwable?)
}
