package cc.midolog.file.port.repository

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class FileMetaRepositoryPortContract {

    abstract fun port(): FileMetaRepositoryPort
    abstract fun clock(): Clock
    open fun reset() {}

    @AfterEach
    fun tearDown() {
        reset()
    }

    @Test
    fun `save, find, update and expire pending the same FileMeta contract`() = runBlocking {
        val repository = port()
        val fixedClock = clock()
        val now = fixedClock.instant().truncatedTo(ChronoUnit.MILLIS)

        val meta = FileMeta(
            id = "file_contract_1",
            ownerId = "owner_1",
            storageKey = "key_1",
            sizeBytes = 1024L,
            contentType = "text/plain",
            checksum = "hash123",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        // save
        val saved = repository.save(meta)
        assertEquals(meta, saved)

        // find
        assertEquals(meta, repository.findById(meta.id))

        // updateStatus (success) & test updatedAt change based on clock
        assertTrue(repository.updateStatus(meta.id, FileStatus.READY))
        val updatedMeta = repository.findById(meta.id)!!
        assertEquals(FileStatus.READY, updatedMeta.status)
        assertEquals(now, updatedMeta.updatedAt) // Because we use fixed clock, it should exactly match

        // updateStatus (not found)
        assertFalse(repository.updateStatus("unknown_id", FileStatus.READY))
    }

    @Test
    fun `findById returns null when not found`() = runBlocking {
        val repository = port()
        assertNull(repository.findById("unknown_id"))
    }
    
    @Test
    fun `save overwrites existing data (upsert)`() = runBlocking {
        val repository = port()
        val fixedClock = clock()
        val now = fixedClock.instant().truncatedTo(ChronoUnit.MILLIS)

        val meta = FileMeta(
            id = "file_contract_1",
            ownerId = "owner_1",
            storageKey = "key_1",
            sizeBytes = null,
            contentType = null,
            checksum = null,
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        repository.save(meta)
        
        val updated = meta.copy(
            sizeBytes = 1024L,
            contentType = "text/plain",
            checksum = "hash123",
            status = FileStatus.READY,
            updatedAt = now.plusSeconds(10)
        )
        repository.save(updated)
        
        assertEquals(updated, repository.findById(meta.id))
    }
    
    @Test
    fun `updateStatus on non-existent id returns false without throwing`() = runBlocking {
        val repository = port()
        assertFalse(repository.updateStatus("non_existent_id", FileStatus.READY))
    }

    @Test
    fun `findExpiredPending returns only pending before cutoff, ordered by updatedAt, up to limit`() = runBlocking {
        val repository = port()
        val now = clock().instant().truncatedTo(ChronoUnit.MILLIS)

        val pendingOld1 = FileMeta("pending_old1", "owner", "k", null, null, null, FileStatus.PENDING, now, now.minusSeconds(100))
        repository.save(pendingOld1)
        
        val pendingOld2 = FileMeta("pending_old2", "owner", "k", null, null, null, FileStatus.PENDING, now, now.minusSeconds(90))
        repository.save(pendingOld2)
        
        val pendingOld3 = FileMeta("pending_old3", "owner", "k", null, null, null, FileStatus.PENDING, now, now.minusSeconds(80))
        repository.save(pendingOld3)
        
        val readyOld = FileMeta("ready_old", "owner", "k", null, null, null, FileStatus.READY, now, now.minusSeconds(100))
        repository.save(readyOld)
        
        val pendingNew = FileMeta("pending_new", "owner", "k", null, null, null, FileStatus.PENDING, now, now)
        repository.save(pendingNew)

        // cutoff: now - 85s. Should match old1, old2.
        val expired = repository.findExpiredPending(now.minusSeconds(85), 2)
        assertEquals(2, expired.size)
        assertEquals("pending_old1", expired[0].id)
        assertEquals("pending_old2", expired[1].id)
        
        // cutoff: now - 85s, but limit 1. Should match old1.
        val expiredLimit1 = repository.findExpiredPending(now.minusSeconds(85), 1)
        assertEquals(1, expiredLimit1.size)
        assertEquals("pending_old1", expiredLimit1[0].id)
        
        // boundary check: exactly matching cutoff should NOT be included
        val expiredBoundary = repository.findExpiredPending(now.minusSeconds(90), 10)
        assertEquals(1, expiredBoundary.size)
        assertEquals("pending_old1", expiredBoundary[0].id)
    }
    
    @Test
    fun `findExpiredPending fails on non-positive limit`() = runBlocking {
        val repository = port()
        val now = clock().instant().truncatedTo(ChronoUnit.MILLIS)
        
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.findExpiredPending(now, 0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.findExpiredPending(now, -1)
        }
        Unit
    }
}
