package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.PresignedRequest
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FilePresignPort
import cc.midolog.file.port.storage.FileStoragePort
import cc.midolog.web.exception.ApiException
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Optional
import java.util.UUID

/**
 * 파일 업로드, 조회, 삭제, 사전 서명 발급 및 사후 검증(Finalize)을 총괄하는 비즈니스 서비스.
 *
 * 서버 경유 스트리밍 업로드뿐만 아니라 S3 등 객체 스토리지를 위한 Presigned PUT 발급과
 * 업로드 완료 후 실제 스토리지의 HEAD 메타데이터를 직접 대조하여 정합성을 검증하는 사후 전이 로직을 제공한다.
 */
@Service
class FileService(
    private val clock: Clock,
    private val fileStoragePort: FileStoragePort,
    private val fileMetaRepositoryPort: FileMetaRepositoryPort,
    filePresignPort: Optional<FilePresignPort> = Optional.empty(),
) {
    private val presignPort: FilePresignPort? = filePresignPort.orElse(null)

    /**
     * 서버 경유 스트리밍 방식으로 파일을 스토리지에 업로드하고 메타데이터를 영속화한다.
     */
    suspend fun uploadFile(
        ownerId: String,
        contentType: String,
        reader: ChunkReader,
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
            updatedAt = now,
        )
        fileMeta = fileMetaRepositoryPort.save(fileMeta)

        try {
            val stored = fileStoragePort.store(
                key = storageKey,
                reader = reader,
                knownSize = null,
                contentType = contentType,
                expectedChecksum = null,
            )

            fileMeta = fileMeta.copy(
                sizeBytes = stored.sizeBytes,
                contentType = stored.contentType,
                checksum = stored.checksum,
                status = FileStatus.READY,
                updatedAt = clock.instant(),
            )
            return fileMetaRepositoryPort.save(fileMeta)
        } catch (e: Exception) {
            fileMetaRepositoryPort.updateStatus(id, FileStatus.FAILED)
            throw e
        }
    }

    /**
     * 클라이언트가 스토리지에 파일을 직접 업로드할 수 있도록 사전 서명된 명세를 생성하고 PENDING 메타데이터를 저장한다.
     */
    suspend fun presignUpload(
        ownerId: String,
        contentType: String,
        expectedSize: Long?,
        expectedChecksum: String?,
        expirationSeconds: Long = 300L,
    ): Pair<FileMeta, PresignedRequest> {
        val presign = presignPort ?: throw UnsupportedOperationException("Presigned upload is not supported by current storage provider")
        if (!presign.isSupported) {
            throw UnsupportedOperationException("Presigned upload is not supported by current storage provider")
        }

        val id = cc.midolog.business.util.IdGenerator.generateUlid()
        val storageKey = UUID.randomUUID().toString()
        val now = clock.instant()

        val fileMeta = FileMeta(
            id = id,
            ownerId = ownerId,
            storageKey = storageKey,
            sizeBytes = expectedSize,
            contentType = contentType,
            checksum = expectedChecksum,
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        val savedMeta = fileMetaRepositoryPort.save(fileMeta)

        val presignedRequest = presign.presignUpload(
            key = storageKey,
            expirationSeconds = expirationSeconds,
            expectedSize = expectedSize,
            contentType = contentType,
            expectedChecksum = expectedChecksum,
        )

        return Pair(savedMeta, presignedRequest)
    }

    /**
     * 클라이언트가 사전 서명 URL로 업로드를 마친 뒤 호출하는 완료(Finalize) 콜백을 처리한다.
     *
     * 클라이언트 주장을 배제하고 스토리지의 실제 HEAD 메타데이터(크기, 체크섬 등)를 직접 조회하여 검증하며,
     * 성공 시 READY로 전이하고 실패 시 FAILED로 마킹 후 고아 객체를 물리 삭제한다.
     * 이미 READY 상태인 경우 멱등성을 보장하여 성공 응답을 반환한다.
     */
    suspend fun finalizeUpload(
        id: String,
        ownerId: String,
        maxSizeBytes: Long,
    ): FileMeta {
        val file = fileMetaRepositoryPort.findById(id) ?: throw ApiException.notFound("file not found")
        if (file.ownerId != ownerId) {
            throw ApiException.notFound("file not found")
        }

        // 이미 완료된 경우 멱등 반환
        if (file.status == FileStatus.READY) {
            return file
        }

        if (file.status != FileStatus.PENDING) {
            throw ApiException.invalidInput("File cannot be finalized in status: ${file.status}")
        }

        val metadata = fileStoragePort.head(file.storageKey)
        if (metadata == null) {
            failAndCleanup(id, file.storageKey)
            throw ApiException.invalidInput("File does not exist in storage")
        }

        if (metadata.sizeBytes <= 0) {
            failAndCleanup(id, file.storageKey)
            throw ApiException.invalidInput("File size must be positive")
        }

        if (metadata.sizeBytes > maxSizeBytes) {
            failAndCleanup(id, file.storageKey)
            throw ApiException.invalidInput("File size exceeds maximum allowed limit")
        }

        if (file.sizeBytes != null && file.sizeBytes != metadata.sizeBytes) {
            failAndCleanup(id, file.storageKey)
            throw ApiException.invalidInput("File size does not match expected size")
        }

        if (file.checksum != null && metadata.checksum != null && file.checksum != metadata.checksum) {
            failAndCleanup(id, file.storageKey)
            throw ApiException.invalidInput("File checksum does not match expected checksum")
        }

        val readyMeta = file.copy(
            sizeBytes = metadata.sizeBytes,
            contentType = metadata.contentType ?: file.contentType,
            checksum = metadata.checksum ?: file.checksum,
            status = FileStatus.READY,
            updatedAt = clock.instant(),
        )
        return fileMetaRepositoryPort.save(readyMeta)
    }

    private suspend fun failAndCleanup(id: String, storageKey: String) {
        fileMetaRepositoryPort.updateStatus(id, FileStatus.FAILED)
        fileStoragePort.delete(storageKey)
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
