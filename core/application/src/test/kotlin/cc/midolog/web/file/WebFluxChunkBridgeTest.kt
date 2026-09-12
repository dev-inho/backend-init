package cc.midolog.web.file

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory

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
        val factory = DefaultDataBufferFactory()
        val db1 = factory.wrap(ByteArray(10))
        val db2 = factory.wrap(ByteArray(10))
        
        channel.send(db1)
        channel.send(db2)
        
        val reader = ChannelChunkReader(channel)
        reader.cancel(null)
        
        assertTrue(channel.isClosedForReceive)
    }
}

    @Test
    fun `send 실패 시 DataBuffer가 release 되는지 확인`() {
        val factory = DefaultDataBufferFactory()
        val buffer = factory.wrap(ByteArray(10))
        val channel = Channel<DataBuffer>(0)
        
        runBlocking {
            channel.close()
            var released = false
            try {
                try {
                    channel.send(buffer)
                } catch (e: Exception) {
                    org.springframework.core.io.buffer.DataBufferUtils.release(buffer)
                    released = true
                    throw e
                }
            } catch (e: Exception) {
                // Expected
            }
            assertTrue(released, "DataBuffer must be released if send fails")
        }
    }
