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

        val saved = port().save(file)
        assertEquals(file, saved)
        
        val loaded = port().findById("file-test-1")
        assertEquals(file, loaded)
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
        val saved = port().save(updatedFile)
        assertEquals(updatedFile, saved)

        val loaded = port().findById("file-upsert-1")
        assertEquals(updatedFile, loaded)
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
        val old1 = FileMeta(
            id = "file-old-1",
            ownerId = "owner-1",
            storageKey = "key-1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now.minusSeconds(7200),
            updatedAt = now.minusSeconds(7200) // oldest
        )
        val old2 = FileMeta(
            id = "file-old-2",
            ownerId = "owner-2",
            storageKey = "key-2",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now.minusSeconds(3600),
            updatedAt = now.minusSeconds(3600) // second oldest
        )
        val old3 = FileMeta(
            id = "file-old-3",
            ownerId = "owner-3",
            storageKey = "key-3",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now.minusSeconds(1800),
            updatedAt = now.minusSeconds(1800) // exact cutoff
        )
        val readyOld = FileMeta(
            id = "file-ready-old",
            ownerId = "owner-4",
            storageKey = "key-4",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.READY,
            createdAt = now.minusSeconds(7200),
            updatedAt = now.minusSeconds(7200)
        )
        val recent = FileMeta(
            id = "file-recent",
            ownerId = "owner-5",
            storageKey = "key-5",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        
        port().save(old1)
        port().save(old2)
        port().save(old3)
        port().save(readyOld)
        port().save(recent)

        val cutoff = now.minusSeconds(1800)
        
        // Fetch up to limit = 2.
        // We do NOT filter the result. We expect EXACTLY 2 items returned, ordered by updatedAt.
        val expired = port().findExpiredPending(cutoff, limit = 2)
        
        // Exact cutoff is EXCLUDED. Only old1 and old2 should be returned.
        assertEquals(2, expired.size)
        assertEquals("file-old-1", expired[0].id)
        assertEquals("file-old-2", expired[1].id)
    }

    @Test
    fun `findExpiredPending fails on non-positive limit`() = runTestBlocking {
        val now = clock().instant()
        var thrownForZero = false
        try {
            port().findExpiredPending(cutoff = now, limit = 0)
        } catch (e: IllegalArgumentException) {
            thrownForZero = true
        }
        assertTrue(thrownForZero)
        
        var thrownForNegative = false
        try {
            port().findExpiredPending(cutoff = now, limit = -1)
        } catch (e: IllegalArgumentException) {
            thrownForNegative = true
        }
        assertTrue(thrownForNegative)
    }
}
