package cc.midolog.util.ext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionExtensionsTest {

    // --- orEmpty 테스트 ---

    @Test
    fun `Iterable orEmpty converts null to empty list`() {
        val nullIterable: Iterable<String>? = null
        val result = nullIterable.orEmpty()
        assertEquals(emptyList<String>(), result.toList())
    }

    @Test
    fun `Iterable orEmpty preserves non-null iterable`() {
        val input: Iterable<String>? = listOf("a", "b", "c")
        val result = input.orEmpty()
        assertEquals(listOf("a", "b", "c"), result.toList())
    }

    @Test
    fun `Iterable orEmpty handles empty iterable`() {
        val input: Iterable<String>? = emptyList()
        val result = input.orEmpty()
        assertEquals(emptyList<String>(), result.toList())
    }

    @Test
    fun `Collection orEmpty converts null to empty collection`() {
        val nullCollection: Collection<Int>? = null
        val result = nullCollection.orEmpty()
        assertEquals(emptyList<Int>(), result.toList())
    }

    @Test
    fun `Collection orEmpty preserves non-null collection`() {
        val input: Collection<Int>? = listOf(1, 2, 3)
        val result = input.orEmpty()
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `List orEmpty converts null to empty list`() {
        val nullList: List<Double>? = null
        val result = nullList.orEmpty()
        assertEquals(emptyList<Double>(), result)
    }

    @Test
    fun `List orEmpty preserves non-null list`() {
        val input: List<Double>? = listOf(1.0, 2.0)
        val result = input.orEmpty()
        assertEquals(listOf(1.0, 2.0), result)
    }

    // --- chunkedBy 테스트 ---

    @Test
    fun `chunkedBy returns empty list for empty input`() {
        val input: List<Int> = emptyList()
        val result = input.chunkedBy { it }
        assertEquals(emptyList<List<Int>>(), result)
    }

    @Test
    fun `chunkedBy groups consecutive equal keys`() {
        val input = listOf(1, 1, 2, 2, 2, 1, 3)
        val result = input.chunkedBy { it }
        val expected = listOf(
            listOf(1, 1),
            listOf(2, 2, 2),
            listOf(1),
            listOf(3)
        )
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy handles single element`() {
        val input = listOf(42)
        val result = input.chunkedBy { it }
        val expected = listOf(listOf(42))
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy handles all same keys`() {
        val input = listOf(5, 5, 5, 5)
        val result = input.chunkedBy { it }
        val expected = listOf(listOf(5, 5, 5, 5))
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy handles all different keys`() {
        val input = listOf(1, 2, 3, 4)
        val result = input.chunkedBy { it }
        val expected = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy works with key selector function`() {
        val input = listOf("a", "aa", "b", "bb", "bbb")
        val result = input.chunkedBy { it.first() }
        val expected = listOf(
            listOf("a", "aa"),
            listOf("b", "bb", "bbb")
        )
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy groups data class by property`() {
        data class Person(val name: String, val age: Int)
        val input = listOf(
            Person("Alice", 30),
            Person("Bob", 30),
            Person("Charlie", 25),
            Person("Diana", 25)
        )
        val result = input.chunkedBy { it.age }
        val expected = listOf(
            listOf(Person("Alice", 30), Person("Bob", 30)),
            listOf(Person("Charlie", 25), Person("Diana", 25))
        )
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy groups nullable keys correctly`() {
        val input = listOf(1, 1, null, null, 2, 2)
        val result = input.chunkedBy { it }
        val expected = listOf(
            listOf(1, 1),
            listOf(null, null),
            listOf(2, 2)
        )
        assertEquals(expected, result)
    }

    @Test
    fun `chunkedBy preserves element order`() {
        val input = listOf(1, 1, 2, 1, 1, 2, 2)
        val result = input.chunkedBy { it }
        val expected = listOf(
            listOf(1, 1),
            listOf(2),
            listOf(1, 1),
            listOf(2, 2)
        )
        assertEquals(expected, result)
    }
}
