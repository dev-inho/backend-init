package cc.midolog.web.file

import cc.midolog.business.service.FileService
import cc.midolog.web.exception.ApiException
import cc.midolog.web.file.dto.FileResponse
import cc.midolog.web.response.ApiResponse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.flux
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/api/files")
class FileController(
    private val fileService: FileService,
    @Value("\${storage.file.max-size-bytes:10485760}") private val maxSizeBytes: Long,
    @Value("\${storage.file.allowed-content-types:}") private val allowedContentTypes: List<String>
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun upload(
        @RequestPart("file") filePart: FilePart,
        authentication: Authentication
    ): ApiResponse<FileResponse> = coroutineScope {
        val ownerId = authentication.name
        val contentType = filePart.headers().contentType?.toString() ?: MediaType.APPLICATION_OCTET_STREAM_VALUE

        if (allowedContentTypes.isNotEmpty() && !allowedContentTypes.contains(contentType)) {
            throw org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
                "Content type $contentType is not allowed"
            )
        }

        val channel = kotlinx.coroutines.channels.Channel<DataBuffer>(1)
        val job = launch {
            try {
                filePart.content().asFlow().collect { channel.send(it) }
            } catch (e: Exception) {
                // ignored or logged
            } finally {
                channel.close()
            }
        }

        val baseReader = ChannelChunkReader(channel)
        val reader = SizeLimitChunkReader(baseReader, maxSizeBytes)

        try {
            val storedFile = fileService.uploadFile(ownerId, contentType, reader)
            ApiResponse.ok(FileResponse.from(storedFile))
        } finally {
            job.cancel()
        }
    }

    @GetMapping("/{id}")
    suspend fun getInfo(
        @PathVariable id: String,
        authentication: Authentication
    ): ApiResponse<FileResponse> {
        val ownerId = authentication.name
        val storedFile = fileService.getFile(id, ownerId)
        return ApiResponse.ok(FileResponse.from(storedFile))
    }

    @DeleteMapping("/{id}")
    suspend fun delete(
        @PathVariable id: String,
        authentication: Authentication
    ): ApiResponse<Unit> {
        val ownerId = authentication.name
        fileService.deleteFile(id, ownerId)
        return ApiResponse.ok(Unit)
    }

    @GetMapping("/{id}/content")
    suspend fun download(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<Flux<DataBuffer>> {
        val ownerId = authentication.name
        val storedFile = fileService.getFile(id, ownerId)
        
        val flux = flux<DataBuffer> {
            val chunkReader = fileService.loadContent(id, ownerId)
            val buffer = ByteArray(8192)
            val factory = DefaultDataBufferFactory.sharedInstance
            try {
                while (true) {
                    val bytesRead = chunkReader.readChunk(buffer)
                    if (bytesRead == -1) break
                    val dataBuffer = factory.allocateBuffer(bytesRead)
                    dataBuffer.write(buffer, 0, bytesRead)
                    send(dataBuffer)
                }
            } catch (e: Exception) {
                chunkReader.cancel(e)
                throw e
            } finally {
                chunkReader.close()
            }
        }
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, storedFile.contentType)
            .header(HttpHeaders.CONTENT_LENGTH, storedFile.sizeBytes.toString())
            .body(flux)
    }
}
