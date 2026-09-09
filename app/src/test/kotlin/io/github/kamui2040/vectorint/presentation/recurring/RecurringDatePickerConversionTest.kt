package io.github.kamui2040.vectorint.presentation.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class RecurringDatePickerConversionTest {
    @Test
    fun `date picker conversion is exact before and after the Unix epoch`() {
        listOf(
            LocalDate.of(1960, 2, 29),
            LocalDate.of(1970, 1, 1),
            LocalDate.of(2026, 9, 15),
        ).forEach { date ->
            assertEquals(date, localDateFromDatePickerMillis(date.toDatePickerMillis()))
        }
    }

    @Test
    fun `date picker conversion rejects time-bearing values`() {
        assertThrows(IllegalArgumentException::class.java) {
            localDateFromDatePickerMillis(LocalDate.of(2026, 9, 15).toDatePickerMillis() + 1)
        }
    }
}
