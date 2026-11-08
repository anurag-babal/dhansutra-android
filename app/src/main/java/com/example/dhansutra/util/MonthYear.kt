package com.example.dhansutra.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Helpers for the "yyyy-MM" month key used across the app. Uses the device's default time zone. */
object MonthYear {
    fun key(ym: YearMonth): String = ym.toString() // ISO yyyy-MM
    fun parse(key: String): YearMonth = YearMonth.parse(key)

    fun current(zone: ZoneId = ZoneId.systemDefault()): YearMonth = YearMonth.now(zone)

    fun of(millis: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    /** Inclusive start of the month in epoch millis. */
    fun startMillis(ym: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long =
        ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Inclusive end of the month (last millisecond) in epoch millis. */
    fun endMillis(ym: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long =
        ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun startOfDayMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun toLocalDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** e.g. "Nov 2026". */
    fun label(ym: YearMonth): String =
        "${ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${ym.year}"

    /** e.g. "Nov". */
    fun shortLabel(ym: YearMonth): String = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** Months from [from] to [to], inclusive. Empty if from > to. */
    fun range(from: YearMonth, to: YearMonth): List<YearMonth> {
        val out = mutableListOf<YearMonth>()
        var m = from
        while (!m.isAfter(to)) {
            out += m
            m = m.plusMonths(1)
        }
        return out
    }
}
