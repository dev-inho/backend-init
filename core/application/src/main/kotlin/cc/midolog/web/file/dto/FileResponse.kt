package cc.midolog.web.file.dto

import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.FileMeta

data class FileResponse(
    val id: String,
    val sizeBytes: Long?,
    val contentType: String?,
    val status: FileStatus
) {
    companion object {
        fun from(file: FileMeta) = FileResponse(
            id = file.id,
            sizeBytes = file.sizeBytes,
            contentType = file.contentType,
            status = file.status
        )
    }
}
