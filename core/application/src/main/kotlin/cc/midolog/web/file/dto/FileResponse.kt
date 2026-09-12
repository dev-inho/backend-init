package cc.midolog.web.file.dto

import cc.midolog.file.model.StoredFile

data class FileResponse(
    val id: String,
    val storageKey: String,
    val sizeBytes: Long,
    val contentType: String,
    val checksum: String?,
    val status: String,
) {
    companion object {
        fun from(file: StoredFile) = FileResponse(
            id = file.id,
            storageKey = file.storageKey,
            sizeBytes = file.sizeBytes,
            contentType = file.contentType,
            checksum = file.checksum,
            status = file.status.name,
        )
    }
}
