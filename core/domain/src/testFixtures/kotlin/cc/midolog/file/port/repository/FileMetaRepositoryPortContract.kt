package cc.midolog.file.port.repository

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.support.runTestBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant

abstract class FileMetaRepositoryPortContract {
    protected abstract fun port(): FileMetaRepositoryPort
    protected abstract fun clock(): Clock

    @Test
    fun `save and load the same FileMeta contract`() = runTestBlocking {
        val now = clock().instant()
        val file = FileMeta(
            id = "file-test-1",
            ownerId = "owner-1",
            storageKey = "storage-key-1",
            sizeBytes = 1024L,
            contentType = "text/plain",
            checksum = "abc123checksum",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        port().save(file)
        val loaded = port().findById("file-test-1")

        assertEquals(file.id, loaded?.id)
        assertEquals(file.status, loaded?.status)
    }

    @Test
    fun `findById returns null when not found`() = runTestBlocking {
        assertNull(port().findById("non-existent-file"))
    }

    @Test
    fun `save overwrites existing data (upsert)`() = runTestBlocking {
        val now = clock().instant()
        val file = FileMeta(
            id = "file-upsert-1",
            ownerId = "owner-1",
            storageKey = "storage-key-1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        port().save(file)

        val updatedFile = file.copy(
            sizeBytes = 2048L,
            contentType = "application/json",
            status = FileStatus.READY,
            updatedAt = now.plusSeconds(60)
        )
        port().save(updatedFile)

        val loaded = port().findById("file-upsert-1")
        assertEquals(2048L, loaded?.sizeBytes)
        assertEquals("application/json", loaded?.contentType)
        assertEquals(FileStatus.READY, loaded?.status)
    }

    @Test
    fun `updateStatus on non-existent id returns false without throwing`() = runTestBlocking {
        assertFalse(port().updateStatus("non-existent", FileStatus.READY))
    }

    @Test
    fun `save, find, update and expire pending the same FileMeta contract`() = runTestBlocking {
        val now = clock().instant()
        val file = FileMeta(
            id = "file-expire-1",
            ownerId = "owner-1",
            storageKey = "storage-key-1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        port().save(file)

        val updated = port().updateStatus("file-expire-1", FileStatus.READY)
        assertTrue(updated)

        val loaded = port().findById("file-expire-1")
        assertEquals(FileStatus.READY, loaded?.status)
        assertEquals(now, loaded?.updatedAt)

        val pending = port().findExpiredPending(cutoff = now.plusSeconds(3600), limit = 10)
        assertTrue(pending.none { it.id == "file-expire-1" })
    }

    @Test
    fun `findExpiredPending returns only pending before cutoff, ordered by updatedAt, up to limit`() = runTestBlocking {
        val now = clock().instant()
        val file1 = FileMeta(
            id = "file-expired-1",
            ownerId = "owner-1",
            storageKey = "storage-key-1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now.minusSeconds(7200),
            updatedAt = now.minusSeconds(7200)
        )
        val file2 = FileMeta(
            id = "file-expired-2",
            ownerId = "owner-2",
            storageKey = "storage-key-2",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now.minusSeconds(3600),
            updatedAt = now.minusSeconds(3600)
        )
        val fileReady = FileMeta(
            id = "file-ready-1",
            ownerId = "owner-3",
            storageKey = "storage-key-3",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.READY,
            createdAt = now.minusSeconds(7200),
            updatedAt = now.minusSeconds(7200)
        )
        val fileRecent = FileMeta(
            id = "file-recent-1",
            ownerId = "owner-4",
            storageKey = "storage-key-4",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        port().save(file1)
        port().save(file2)
        port().save(fileReady)
        port().save(fileRecent)

        val cutoff = now.minusSeconds(1800)
        val expired = port().findExpiredPending(cutoff, limit = 1)
        
        assertEquals(1, expired.size)
        assertEquals("file-expired-1", expired[0].id)
    }

    @Test
    fun `findExpiredPending fails on non-positive limit`() = runTestBlocking {
        val now = clock().instant()
        var exceptionThrown = false
        try {
            port().findExpiredPending(cutoff = now, limit = 0)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw IllegalArgumentException on non-positive limit")
    }
}
