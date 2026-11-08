package com.example.dhansutra

import com.example.dhansutra.ui.analytics.niceCeil
import com.example.dhansutra.ui.analytics.percentOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartsTest {
    @Test
    fun niceCeilRoundsUpToTidyValues() {
        assertEquals(1L, niceCeil(0))
        assertEquals(5L, niceCeil(3))
        assertEquals(10L, niceCeil(10))
        assertEquals(20L, niceCeil(11))
        assertEquals(500_000L, niceCeil(390_000))
        assertEquals(1_000_000L, niceCeil(600_000))
    }

    @Test
    fun percentOfHandlesZeroTotal() {
        assertEquals(0f, percentOf(5, 0), 0f)
        assertEquals(25f, percentOf(1, 4), 0.001f)
    }
}
