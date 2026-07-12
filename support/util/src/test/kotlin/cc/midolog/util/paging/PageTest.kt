package cc.midolog.util.paging

import cc.midolog.util.UtilDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PageTest {

    // Direction Tests
    @Test
    fun `Direction enum has ASC and DESC values`() {
        assertEquals(2, Direction.entries.size)
        assertTrue(Direction.entries.contains(Direction.ASC))
        assertTrue(Direction.entries.contains(Direction.DESC))
    }

    // Sort Tests
    @Test
    fun `Sort with valid property and default direction`() {
        val sort = Sort("name")
        assertEquals("name", sort.property)
        assertEquals(Direction.ASC, sort.direction)
    }

    @Test
    fun `Sort with custom direction`() {
        val sort = Sort("age", Direction.DESC)
        assertEquals("age", sort.property)
        assertEquals(Direction.DESC, sort.direction)
    }

    @Test
    fun `Sort rejects blank property`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sort("")
        }
    }

    @Test
    fun `Sort rejects whitespace-only property`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sort("   ")
        }
    }

    @Test
    fun `Sort rejects property with only tabs`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sort("\t\t")
        }
    }

    // PageRequest Tests
    @Test
    fun `PageRequest with default size`() {
        val request = PageRequest(0)
        assertEquals(0, request.page)
        assertEquals(UtilDefaults.DEFAULT_PAGE_SIZE, request.size)
        assertEquals(emptyList<Sort>(), request.sorts)
    }

    @Test
    fun `PageRequest with custom size`() {
        val request = PageRequest(5, 50)
        assertEquals(5, request.page)
        assertEquals(50, request.size)
    }

    @Test
    fun `PageRequest with sorts`() {
        val sorts = listOf(Sort("name", Direction.ASC), Sort("age", Direction.DESC))
        val request = PageRequest(0, 20, sorts)
        assertEquals(sorts, request.sorts)
    }

    @Test
    fun `PageRequest rejects negative page`() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRequest(-1)
        }
    }

    @Test
    fun `PageRequest rejects page with negative large value`() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRequest(-100)
        }
    }

    @Test
    fun `PageRequest rejects size of 0`() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRequest(0, 0)
        }
    }

    @Test
    fun `PageRequest rejects size exceeding MAX_PAGE_SIZE`() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRequest(0, UtilDefaults.MAX_PAGE_SIZE + 1)
        }
    }

    @Test
    fun `PageRequest accepts size of 1`() {
        val request = PageRequest(0, 1)
        assertEquals(1, request.size)
    }

    @Test
    fun `PageRequest accepts size equal to MAX_PAGE_SIZE`() {
        val request = PageRequest(0, UtilDefaults.MAX_PAGE_SIZE)
        assertEquals(UtilDefaults.MAX_PAGE_SIZE, request.size)
    }

    @Test
    fun `PageRequest calculates offset correctly`() {
        val request = PageRequest(3, 20)
        assertEquals(60L, request.offset)
    }

    @Test
    fun `PageRequest calculates offset correctly for first page`() {
        val request = PageRequest(0, 20)
        assertEquals(0L, request.offset)
    }

    @Test
    fun `PageRequest calculates offset with large page number`() {
        val request = PageRequest(1000, 50)
        assertEquals(50000L, request.offset)
    }

    // Page Tests - totalPages
    @Test
    fun `Page totalPages is 0 when size is 0`() {
        val page = Page<String>(emptyList(), 0, 0, 0)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun `Page totalPages with no content`() {
        val page = Page<String>(emptyList(), 0, 20, 0)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun `Page totalPages when elements exactly divide by size`() {
        val page = Page<String>(listOf("a", "b"), 0, 20, 40)
        assertEquals(2, page.totalPages)
    }

    @Test
    fun `Page totalPages with remainder`() {
        val page = Page<String>(listOf("a"), 0, 20, 25)
        assertEquals(2, page.totalPages)
    }

    @Test
    fun `Page totalPages with single element`() {
        val page = Page<String>(listOf("a"), 0, 20, 1)
        assertEquals(1, page.totalPages)
    }

    @Test
    fun `Page totalPages with large numbers`() {
        val page = Page<String>(emptyList(), 0, 100, 1001)
        assertEquals(11, page.totalPages)
    }

    // Page Tests - hasNext
    @Test
    fun `Page hasNext is false for last page`() {
        val page = Page<String>(emptyList(), 0, 20, 20)
        assertFalse(page.hasNext)
    }

    @Test
    fun `Page hasNext is true when not on last page`() {
        val page = Page<String>(emptyList(), 0, 20, 40)
        assertTrue(page.hasNext)
    }

    @Test
    fun `Page hasNext is true for first page with multiple pages`() {
        val page = Page<String>(emptyList(), 0, 20, 100)
        assertTrue(page.hasNext)
    }

    // Page Tests - hasPrevious
    @Test
    fun `Page hasPrevious is false for first page`() {
        val page = Page<String>(emptyList(), 0, 20, 100)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `Page hasPrevious is true for non-first page`() {
        val page = Page<String>(emptyList(), 1, 20, 100)
        assertTrue(page.hasPrevious)
    }

    @Test
    fun `Page hasPrevious is true for last page`() {
        val page = Page<String>(emptyList(), 2, 20, 60)
        assertTrue(page.hasPrevious)
    }

    // Page Tests - isFirst
    @Test
    fun `Page isFirst is true for page 0`() {
        val page = Page<String>(emptyList(), 0, 20, 100)
        assertTrue(page.isFirst)
    }

    @Test
    fun `Page isFirst is false for page 1`() {
        val page = Page<String>(emptyList(), 1, 20, 100)
        assertFalse(page.isFirst)
    }

    @Test
    fun `Page isFirst is false for higher pages`() {
        val page = Page<String>(emptyList(), 5, 20, 200)
        assertFalse(page.isFirst)
    }

    // Page Tests - isLast
    @Test
    fun `Page isLast is true for last page`() {
        val page = Page<String>(emptyList(), 0, 20, 20)
        assertTrue(page.isLast)
    }

    @Test
    fun `Page isLast is false for first page with multiple pages`() {
        val page = Page<String>(emptyList(), 0, 20, 40)
        assertFalse(page.isLast)
    }

    @Test
    fun `Page isLast is true when on actual last page`() {
        val page = Page<String>(emptyList(), 2, 20, 60)
        assertTrue(page.isLast)
    }

    @Test
    fun `Page isLast is true when page equals totalPages minus 1`() {
        val page = Page<String>(emptyList(), 4, 25, 100)
        assertTrue(page.isLast)
    }

    // Page Tests - Generic content
    @Test
    fun `Page can hold different content types`() {
        val contentInt = listOf(1, 2, 3)
        val pageInt = Page(contentInt, 0, 10, 30)
        assertEquals(contentInt, pageInt.content)
    }

    @Test
    fun `Page with data class content`() {
        /**
         * 테스트용 사용자 데이터 클래스
         *
         * @property id 사용자 ID
         * @property name 사용자명
         */
        data class User(val id: Long, val name: String)
        val users = listOf(User(1, "Alice"), User(2, "Bob"))
        val page = Page(users, 0, 10, 20)
        assertEquals(users, page.content)
        assertEquals(2, page.totalPages)
    }

    // Integration Tests
    @Test
    fun `PageRequest and Page work together correctly`() {
        val request = PageRequest(1, 10)
        val page = Page(listOf("a", "b"), request.page, request.size, 35)

        assertEquals(1, page.page)
        assertEquals(10, page.size)
        assertEquals(4, page.totalPages)
        assertTrue(page.hasNext)
        assertTrue(page.hasPrevious)
        assertFalse(page.isFirst)
        assertFalse(page.isLast)
    }

    @Test
    fun `PageRequest with sorts creates valid request`() {
        val sorts = listOf(
            Sort("createdAt", Direction.DESC),
            Sort("name", Direction.ASC)
        )
        val request = PageRequest(0, 20, sorts)

        assertEquals(2, request.sorts.size)
        assertEquals("createdAt", request.sorts[0].property)
        assertEquals(Direction.DESC, request.sorts[0].direction)
        assertEquals("name", request.sorts[1].property)
        assertEquals(Direction.ASC, request.sorts[1].direction)
    }

    @Test
    fun `Empty page with totalElements zero`() {
        val page = Page<String>(emptyList(), 0, 20, 0)

        assertEquals(0, page.totalPages)
        assertFalse(page.hasNext)
        assertFalse(page.hasPrevious)
        assertTrue(page.isFirst)
        assertTrue(page.isLast)
    }
}
