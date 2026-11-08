package com.example.dhansutra.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyFormatterTest {
    @Test
    fun indianGrouping() {
        assertEquals("₹0.00", CurrencyFormatter.format(0))
        assertEquals("₹999.50", CurrencyFormatter.format(99950))
        assertEquals("₹1,23,456.00", CurrencyFormatter.format(12345600))
        assertEquals("₹1,00,00,000", CurrencyFormatter.format(1_000_000_000, showDecimals = false))
        assertEquals("-₹1,234.05", CurrencyFormatter.format(-123405))
    }

    @Test
    fun compact() {
        assertEquals("₹500", CurrencyFormatter.compact(50000))
        assertEquals("₹12.5K", CurrencyFormatter.compact(1_250_000))
        assertEquals("₹1.2L", CurrencyFormatter.compact(12_000_000))
        assertEquals("₹1.5Cr", CurrencyFormatter.compact(1_500_000_000))
    }

    @Test
    fun parse() {
        assertEquals(125050L, CurrencyFormatter.parseToPaise("1,250.50"))
        assertEquals(100L, CurrencyFormatter.parseToPaise("1"))
        assertNull(CurrencyFormatter.parseToPaise(""))
        assertNull(CurrencyFormatter.parseToPaise("abc"))
        assertNull(CurrencyFormatter.parseToPaise("0"))
        assertNull(CurrencyFormatter.parseToPaise("-5"))
        assertNull(CurrencyFormatter.parseToPaise("1.234"))
        assertEquals("1250.5", CurrencyFormatter.toInputText(125050))
        assertEquals("1000", CurrencyFormatter.toInputText(100000))
    }
}
