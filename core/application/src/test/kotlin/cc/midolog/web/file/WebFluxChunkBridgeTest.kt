package cc.midolog.web.file

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.PooledDataBuffer

class WebFluxChunkBridgeTest {

    @Test
    fun `channel close with cause should throw the same cause in readChunk`() = runBlocking {
        val channel = Channel<DataBuffer>(1)
        val reader = ChannelChunkReader(channel)

        val customException = RuntimeException("Custom cause")
        channel.close(customException)

        val buffer = ByteArray(10)
        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking { reader.readChunk(buffer) }
        }
        assertEquals("Custom cause", exception.message)
    }

    @Test
    fun `cancel should close the channel and release remaining buffers`() = runBlocking {
        val channel = Channel<DataBuffer>(2)
        val db1 = mock(PooledDataBuffer::class.java)
        val db2 = mock(PooledDataBuffer::class.java)

        channel.send(db1)
        channel.send(db2)

        val reader = ChannelChunkReader(channel)
        reader.cancel(null)

        assertTrue(channel.isClosedForReceive)
        verify(db1, times(1)).release()
        verify(db2, times(1)).release()
    }

    @Test
    fun `sendAndReleaseOnFailure should release buffer when send fails`() = runBlocking {
        val channel = Channel<DataBuffer>(0) // rendezvous channel
        val buffer = mock(PooledDataBuffer::class.java)

        channel.close() // Close the channel to force send failure

        assertThrows(Exception::class.java) {
            runBlocking {
                channel.sendAndReleaseOnFailure(buffer)
            }
        }

        verify(buffer, times(1)).release()
    }
}
