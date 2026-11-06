package com.sitticun.orderapp.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone
import kotlin.math.abs

class FormattersTest {
    @Test fun currentTimestampIsUtcRegardlessOfDeviceTimezone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"))
            val before = Instant.now()
            val actual = Instant.parse(Formatters.getCurrentIsoTimestamp())
            assertTrue(abs(actual.epochSecond - before.epochSecond) <= 2)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
