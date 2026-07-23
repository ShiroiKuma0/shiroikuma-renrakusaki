package org.fossify.contacts.helpers

import android.content.Context
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.contacts.extensions.config
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

// Timestamp formatting for the 詳 detail lines (last call / last message), ported from our Phone
// fork (denwa). The format is a UI-page setting: Japanese readings, the system date/time format,
// or plain 24-/12-hour clocks. Non-Japanese modes prefix a numeric date on other days.

const val TIME_FORMAT_JAPANESE = 0
const val TIME_FORMAT_SYSTEM = 1
const val TIME_FORMAT_24H = 2
const val TIME_FORMAT_12H = 3

/** The timestamp per the configured detail time format. */
fun Long.formatDetailTime(context: Context): String = when (context.config.detailTimeFormat) {
    TIME_FORMAT_24H, TIME_FORMAT_12H -> formatWithConfiguredPatterns(context)
    TIME_FORMAT_SYSTEM -> formatDateOrTime(context, hideTimeOnOtherDays = false, showCurrentYear = false)
    else -> toJapaneseDateTimeString()
}

/** The built-in pattern for an age bracket (today / this year / older) in a 24-/12-hour mode. */
fun defaultDetailPattern(kindPrefix: String, mode: Int): String {
    val clock = if (mode == TIME_FORMAT_12H) "h:mm a" else "H:mm"
    return when (kindPrefix) {
        DETAIL_PATTERN_TODAY_PREFIX -> clock
        DETAIL_PATTERN_YEAR_PREFIX -> "d.M. $clock"
        else -> "d.M.yyyy $clock"
    }
}

/** The user-editable pattern for a timestamp's age bracket, applied safely. */
fun Long.applyDetailPattern(context: Context, pattern: String): String {
    val kind = detailPatternKind()
    return formatSafely(pattern, defaultDetailPattern(kind, context.config.detailTimeFormat))
}

/** Which age-bracket pattern this timestamp uses (today / earlier this year / older). */
fun Long.detailPatternKind(): String {
    val date = toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> DETAIL_PATTERN_TODAY_PREFIX
        date.year == today.year -> DETAIL_PATTERN_YEAR_PREFIX
        else -> DETAIL_PATTERN_OLDER_PREFIX
    }
}

// 24-/12-hour modes: fully user-editable patterns, one per age bracket, kept per mode.
private fun Long.formatWithConfiguredPatterns(context: Context): String {
    val mode = context.config.detailTimeFormat
    val kind = detailPatternKind()
    return formatSafely(context.config.getDetailPattern(kind, mode), defaultDetailPattern(kind, mode))
}

// An invalid user pattern falls back to the built-in default instead of crashing the list.
private fun Long.formatSafely(pattern: String, fallback: String): String = try {
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(this))
} catch (e: IllegalArgumentException) {
    SimpleDateFormat(fallback, Locale.getDefault()).format(Date(this))
}

// Sino-Japanese clock readings: e.g. 14:53 -> 午後二時五十三分, 9:30 -> 午前九時半.
// :00 drops the minute part, :30 becomes 半; noon/midnight get the special words 正午 / 正子.
fun Long.toJapaneseClockString(): String {
    val time = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    val hour = time.hour
    val minute = time.minute
    when {
        hour == 12 && minute == 0 -> return "正午"
        hour == 12 && minute == 30 -> return "正午半"
        hour == 0 && minute == 0 -> return "正子"
        hour == 0 && minute == 30 -> return "正子半"
    }
    val period = if (hour < 12) "午前" else "午後"
    val hour12 = when {
        hour == 0 -> 12
        hour <= 12 -> hour
        else -> hour - 12
    }
    val minutePart = when (minute) {
        0 -> ""
        30 -> "半"
        else -> "${minute.toKanjiNumeral()}分"
    }
    return "$period${hour12.toKanjiNumeral()}時$minutePart"
}

// Today -> just the clock; yesterday -> 昨日; this year -> 六月三十日; older -> 令和七年三月五日.
private fun Long.toJapaneseDateTimeString(): String {
    val date = toLocalDate()
    val today = LocalDate.now()
    val clock = toJapaneseClockString()
    return when {
        date == today -> clock
        date == today.minusDays(1) -> "昨日 $clock"
        date.year == today.year ->
            "${date.monthValue.toKanjiNumeral()}月${date.dayOfMonth.toKanjiNumeral()}日 $clock"

        else -> "${date.toImperialDateString()} $clock"
    }
}

// A compact imperial-era (和暦) date, e.g. 令和七年五月二十九日. Era boundaries are fixed so the
// output does not depend on JapaneseEra API availability; the era's first year is written 元年.
private fun LocalDate.toImperialDateString(): String {
    val (era, baseYear) = when {
        !isBefore(LocalDate.of(2019, 5, 1)) -> "令和" to 2018
        !isBefore(LocalDate.of(1989, 1, 8)) -> "平成" to 1988
        !isBefore(LocalDate.of(1926, 12, 25)) -> "昭和" to 1925
        !isBefore(LocalDate.of(1912, 7, 30)) -> "大正" to 1911
        else -> "明治" to 1867
    }
    val eraYear = year - baseYear
    val yearPart = if (eraYear == 1) "元" else eraYear.toKanjiNumeral()
    return "$era${yearPart}年${monthValue.toKanjiNumeral()}月${dayOfMonth.toKanjiNumeral()}日"
}

// Converts 1..99 to everyday kanji numerals (e.g. 29 -> 二十九) — covers era years, months and days.
internal fun Int.toKanjiNumeral(): String {
    if (this <= 0) {
        return "〇"
    }
    val digits = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val tens = this / 10
    val ones = this % 10
    return buildString {
        when (tens) {
            0 -> {}
            1 -> append("十")
            else -> append(digits[tens]).append("十")
        }
        if (ones != 0) {
            append(digits[ones])
        }
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
