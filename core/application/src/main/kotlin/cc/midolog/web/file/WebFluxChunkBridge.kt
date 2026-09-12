package cc.midolog.web.file

import cc.midolog.file.port.storage.ChunkReader
import kotlinx.coroutines.channels.ReceiveChannel
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import kotlin.math.min

/**
 * Channel 기반 DataBuffer 스트림을 ChunkReader로 연결하는 브릿지.
 */
class ChannelChunkReader(
    private val channel: ReceiveChannel<DataBuffer>
) : ChunkReader {
    private var currentBuffer: DataBuffer? = null

    override suspend fun readChunk(buffer: ByteArray): Int {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            if (currentBuffer == null || currentBuffer!!.readableByteCount() == 0) {
                currentBuffer?.let { DataBufferUtils.release(it) }
                
                val result = channel.receiveCatching()
                if (result.isSuccess) {
                    currentBuffer = result.getOrThrow()
                } else {
                    currentBuffer = null
                    break
                }
            }

            val available = currentBuffer!!.readableByteCount()
            val toRead = min(buffer.size - bytesRead, available)
            currentBuffer!!.read(buffer, bytesRead, toRead)
            bytesRead += toRead
        }
        return if (bytesRead == 0) -1 else bytesRead
    }

    override suspend fun cancel(cause: Throwable?) {
        channel.cancel(cause as? java.util.concurrent.CancellationException)
        close()
    }

    override fun close() {
        currentBuffer?.let {
            DataBufferUtils.release(it)
            currentBuffer = null
        }
    }
}

class SizeLimitChunkReader(
    private val delegate: ChunkReader,
    private val maxSize: Long
) : ChunkReader {
    private var readBytes = 0L

    override suspend fun readChunk(buffer: ByteArray): Int {
        val read = delegate.readChunk(buffer)
        if (read > 0) {
            readBytes += read
            if (readBytes > maxSize) {
                val e = cc.midolog.web.exception.ApiException.payloadTooLarge("File size exceeds maximum limit of $maxSize bytes")
                cancel(e)
                throw e
            }
        }
        return read
    }

    override suspend fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
    }

    override fun close() {
        delegate.close()
    }
}
