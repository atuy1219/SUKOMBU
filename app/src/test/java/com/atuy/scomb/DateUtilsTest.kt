package com.atuy.scomb

import com.atuy.scomb.util.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class DateUtilsTest {
    @Test fun campusDatesDoNotDependOnDeviceTimezone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(Instant.parse("2026-09-06T01:00:00Z").toEpochMilli(),
                DateUtils.stringToTime("2026-09-06 10:00:00", "yyyy-MM-dd HH:mm:ss"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun impossibleDateIsRejected() {
        assertEquals(0L, DateUtils.stringToTime("2026-02-30 10:00:00", "yyyy-MM-dd HH:mm:ss"))
    }

    @Test fun trailingGarbageIsRejected() {
        assertEquals(0L, DateUtils.stringToTime("2026/09/06 10:00oops"))
    }
}
