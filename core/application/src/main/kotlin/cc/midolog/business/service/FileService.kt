package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import cc.midolog.web.exception.ApiException
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
class FileService(
    private val clock: Clock,
    private val fileStoragePort: FileStoragePort,
    private val fileMetaRepositoryPort: FileMetaRepositoryPort,
) {
    suspend fun uploadFile(
        ownerId: String,
        contentType: String,
        reader: ChunkReader
    ): FileMeta {
        val id = cc.midolog.business.util.IdGenerator.generateUlid()
        val storageKey = UUID.randomUUID().toString()
        val now = clock.instant()

        var fileMeta = FileMeta(
            id = id,
            ownerId = ownerId,
            storageKey = storageKey,
            sizeBytes = null,
            contentType = null,
            checksum = null,
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        fileMeta = fileMetaRepositoryPort.save(fileMeta)

        try {
            val stored = fileStoragePort.store(
                key = storageKey,
                reader = reader,
                knownSize = null,
                contentType = contentType,
                expectedChecksum = null
            )

            fileMeta = fileMeta.copy(
                sizeBytes = stored.sizeBytes,
                contentType = stored.contentType,
                checksum = stored.checksum,
                status = FileStatus.READY,
                updatedAt = clock.instant()
            )
            return fileMetaRepositoryPort.save(fileMeta)
        } catch (e: Exception) {
            fileMetaRepositoryPort.updateStatus(id, FileStatus.FAILED)
            throw e
        }
    }

    suspend fun getFile(id: String, ownerId: String): FileMeta {
        val file = fileMetaRepositoryPort.findById(id) ?: throw ApiException.notFound("file not found")
        if (file.ownerId != ownerId) {
            throw ApiException.notFound("file not found")
        }

        if (file.status == FileStatus.READY) {
            if (file.sizeBytes == null || file.contentType == null) {
                throw IllegalStateException("ready file has missing metadata")
            }
        }

        return file
    }

    suspend fun deleteFile(id: String, ownerId: String) {
        val file = getFile(id, ownerId)
        val deleted = fileStoragePort.delete(file.storageKey)
        if (!deleted) {
            throw IllegalStateException("Failed to delete file from storage")
        }
        fileMetaRepositoryPort.updateStatus(id, FileStatus.DELETED)
    }

    suspend fun loadContent(id: String, ownerId: String): ChunkReader {
        val file = getFile(id, ownerId)
        if (file.status != FileStatus.READY) {
            throw ApiException.invalidInput("file is not ready")
        }
        return fileStoragePort.load(file.storageKey) ?: throw ApiException.notFound("file not found in storage")
    }
}
