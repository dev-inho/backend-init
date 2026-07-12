package cc.midolog.util

import java.time.format.DateTimeFormatter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UtilDefaultsTest {

    @Test
    fun `DEFAULT_PAGE_SIZE는 20이다`() {
        assertEquals(20, UtilDefaults.DEFAULT_PAGE_SIZE)
    }

    @Test
    fun `MAX_PAGE_SIZE는 100이다`() {
        assertEquals(100, UtilDefaults.MAX_PAGE_SIZE)
    }

    @Test
    fun `DEFAULT_PAGE_SIZE는 MAX_PAGE_SIZE 이하여야 한다`() {
        assertTrue(UtilDefaults.DEFAULT_PAGE_SIZE <= UtilDefaults.MAX_PAGE_SIZE)
    }

    @Test
    fun `DEFAULT_DATE_FORMAT은 유효한 DateTimeFormatter 패턴이다`() {
        DateTimeFormatter.ofPattern(UtilDefaults.DEFAULT_DATE_FORMAT)
        // 유효한 패턴이면 예외가 발생하지 않음
    }

    @Test
    fun `DEFAULT_DATETIME_FORMAT은 유효한 DateTimeFormatter 패턴이다`() {
        DateTimeFormatter.ofPattern(UtilDefaults.DEFAULT_DATETIME_FORMAT)
        // 유효한 패턴이면 예외가 발생하지 않음
    }
}
