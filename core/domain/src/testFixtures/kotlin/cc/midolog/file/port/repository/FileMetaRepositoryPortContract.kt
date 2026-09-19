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
        val cutoff = now.minusSeconds(1800)
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
            createdAt = now.minusSeconds(2400),
            updatedAt = now.minusSeconds(2400) // third oldest, strictly before cutoff
        )
        val exactCutoff = FileMeta(
            id = "file-exact-cutoff",
            ownerId = "owner-exact",
            storageKey = "key-exact",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = cutoff,
            updatedAt = cutoff // exact cutoff, must be excluded
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
        port().save(exactCutoff)
        port().save(readyOld)
        port().save(recent)

        // Fetch up to limit = 2.
        // We do NOT filter the result. We expect EXACTLY 2 items returned, ordered by updatedAt.
        val expired = port().findExpiredPending(cutoff, limit = 2)

        // Exact cutoff is EXCLUDED. Only the earliest 2 of 3 expired pending items (old1 and old2) should be returned.
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

    @Test
    fun `updateStatusConditionally succeeds and updates optional fields when current status matches expectedStatuses`() = runTestBlocking {
        val now = clock().instant()
        val file = FileMeta(
            id = "file-cond-success-1",
            ownerId = "owner-cond",
            storageKey = "key-cond-1",
            sizeBytes = 100L,
            contentType = "text/plain",
            checksum = "old-sha",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        port().save(file)

        val updated = port().updateStatusConditionally(
            id = "file-cond-success-1",
            expectedStatuses = setOf(FileStatus.PENDING),
            newStatus = FileStatus.READY,
            sizeBytes = 2048L,
            contentType = "application/json",
            checksum = "new-sha256",
        )
        assertTrue(updated, "Conditional update must succeed when status matches expectedStatuses")

        val loaded = port().findById("file-cond-success-1")
        assertEquals(FileStatus.READY, loaded?.status)
        assertEquals(2048L, loaded?.sizeBytes)
        assertEquals("application/json", loaded?.contentType)
        assertEquals("new-sha256", loaded?.checksum)
    }

    @Test
    fun `updateStatusConditionally fails without modifying state when current status does not match expectedStatuses`() = runTestBlocking {
        val now = clock().instant()
        val file = FileMeta(
            id = "file-cond-fail-1",
            ownerId = "owner-cond",
            storageKey = "key-cond-2",
            sizeBytes = 100L,
            contentType = "text/plain",
            checksum = "sha-orig",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        port().save(file)

        // 기대 상태가 READY인데 현재 상태는 PENDING이므로 실패해야 함 (경쟁 상태 시뮬레이션)
        val updated = port().updateStatusConditionally(
            id = "file-cond-fail-1",
            expectedStatuses = setOf(FileStatus.READY),
            newStatus = FileStatus.DELETED,
        )
        assertFalse(updated, "Conditional update must fail when status does not match expectedStatuses")

        val loaded = port().findById("file-cond-fail-1")
        assertEquals(FileStatus.PENDING, loaded?.status, "State must remain unchanged on CAS failure")
        assertEquals(100L, loaded?.sizeBytes)
        assertEquals("text/plain", loaded?.contentType)
    }

    @Test
    fun `updateStatusConditionally preserves DELETED status against resurrection to READY`() = runTestBlocking {
        val now = clock().instant()
        val file = FileMeta(
            id = "file-cond-deleted-1",
            ownerId = "owner-cond",
            storageKey = "key-cond-3",
            sizeBytes = 500L,
            contentType = "text/plain",
            checksum = "sha-del",
            status = FileStatus.DELETED,
            createdAt = now,
            updatedAt = now,
        )
        port().save(file)

        // 이미 DELETED인 상태에서 늦은 finalize가 PENDING -> READY 전이를 시도해도 차단되어야 함
        val updated = port().updateStatusConditionally(
            id = "file-cond-deleted-1",
            expectedStatuses = setOf(FileStatus.PENDING),
            newStatus = FileStatus.READY,
        )
        assertFalse(updated, "DELETED file must not resurrect to READY on conditional update")

        val loaded = port().findById("file-cond-deleted-1")
        assertEquals(FileStatus.DELETED, loaded?.status, "DELETED status must be preserved")
    }

    @Test
    fun `findExpiredOrphans returns both PENDING and FAILED orphans before cutoff, up to limit, ordered by updatedAt`() = runTestBlocking {
        val now = clock().instant()
        val cutoff = now.minusSeconds(1800)

        val oldPending = FileMeta(
            id = "orphan-old-pending",
            ownerId = "owner-orphan",
            storageKey = "key-p1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now.minusSeconds(7200),
            updatedAt = now.minusSeconds(7200), // earliest
        )
        val oldFailed = FileMeta(
            id = "orphan-old-failed",
            ownerId = "owner-orphan",
            storageKey = "key-f1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.FAILED,
            createdAt = now.minusSeconds(3600),
            updatedAt = now.minusSeconds(3600), // second earliest
        )
        val oldReady = FileMeta(
            id = "orphan-old-ready",
            ownerId = "owner-orphan",
            storageKey = "key-r1",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.READY,
            createdAt = now.minusSeconds(5000),
            updatedAt = now.minusSeconds(5000), // must be excluded
        )
        val recentPending = FileMeta(
            id = "orphan-recent-pending",
            ownerId = "owner-orphan",
            storageKey = "key-p2",
            sizeBytes = 10L, contentType = "text", checksum = "chk",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now, // after cutoff, must be excluded
        )

        port().save(oldPending)
        port().save(oldFailed)
        port().save(oldReady)
        port().save(recentPending)

        // PENDING과 FAILED가 모두 회수 대상에 포함되어야 함
        val orphans = port().findExpiredOrphans(
            cutoff = cutoff,
            limit = 10,
            statuses = setOf(FileStatus.PENDING, FileStatus.FAILED),
        )

        val returnedIds = orphans.map { it.id }
        assertTrue(returnedIds.contains("orphan-old-pending"), "Old PENDING orphan must be included")
        assertTrue(returnedIds.contains("orphan-old-failed"), "Old FAILED orphan must be included")
        assertFalse(returnedIds.contains("orphan-old-ready"), "READY file must be excluded")
        assertFalse(returnedIds.contains("orphan-recent-pending"), "Recent file after cutoff must be excluded")

        // updatedAt 오름차순 정렬 검증 (oldPending -> oldFailed)
        assertEquals("orphan-old-pending", orphans[0].id)
        assertEquals("orphan-old-failed", orphans[1].id)

        // limit 적용 검증
        val limited = port().findExpiredOrphans(
            cutoff = cutoff,
            limit = 1,
            statuses = setOf(FileStatus.PENDING, FileStatus.FAILED),
        )
        assertEquals(1, limited.size)
        assertEquals("orphan-old-pending", limited[0].id)
    }
}
