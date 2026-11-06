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
    @Test fun postgresUtcTimestampDisplaysInDeviceTimezone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok"))
            listOf("2026-11-06T02:35:05+00:00", "2026-11-06T02:35:05.123456+00:00", "2026-11-06T02:35:05Z").forEach {
                assertTrue(Formatters.formatIsoDateTime(it).contains("09:35"))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
