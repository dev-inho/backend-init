package cc.midolog.business.service

import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import cc.midolog.web.exception.ApiException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FileService(
    private val fileStoragePort: FileStoragePort,
    private val fileMetaRepositoryPort: FileMetaRepositoryPort,
) {
    suspend fun uploadFile(
        ownerId: String,
        contentType: String,
        reader: ChunkReader
    ): StoredFile {
        val id = UUID.randomUUID().toString()
        val storageKey = UUID.randomUUID().toString()

        var fileMeta = StoredFile(
            id = id,
            ownerId = ownerId,
            storageKey = storageKey,
            sizeBytes = 0,
            contentType = contentType,
            checksum = null,
            status = FileStatus.PENDING
        )
        fileMetaRepositoryPort.save(fileMeta)

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
                checksum = stored.checksum,
                status = FileStatus.READY
            )
            return fileMetaRepositoryPort.save(fileMeta)
        } catch (e: Exception) {
            fileMetaRepositoryPort.updateStatus(id, FileStatus.FAILED)
            throw e
        }
    }

    suspend fun getFile(id: String, ownerId: String): StoredFile {
        val file = fileMetaRepositoryPort.findById(id) ?: throw ApiException.notFound("file not found")
        // 정보 노출 최소화: 권한 없는 파일의 존재 여부 자체를 숨기기 위해 403 대신 404 반환
        if (file.ownerId != ownerId) {
            throw ApiException.notFound("file not found")
        }
        return file
    }

    suspend fun deleteFile(id: String, ownerId: String) {
        val file = getFile(id, ownerId)
        fileStoragePort.delete(file.storageKey)
        fileMetaRepositoryPort.updateStatus(id, FileStatus.DELETED)
    }

    suspend fun loadContent(id: String, ownerId: String): ChunkReader {
        val file = getFile(id, ownerId)
        if (file.status != FileStatus.READY) {
            throw ApiException.badRequest("file is not ready")
        }
        return fileStoragePort.load(file.storageKey) ?: throw ApiException.notFound("file not found in storage")
    }
}
