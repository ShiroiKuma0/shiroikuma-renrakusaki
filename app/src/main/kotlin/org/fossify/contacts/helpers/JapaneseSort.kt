package org.fossify.contacts.helpers

import org.fossify.commons.extensions.normalizeString
import java.util.Locale

// Japanese-aware bucketing and ordering for the letter-sectioned Contacts list.
//
// Sort keys starting with kana bucket into gojūon rows (あ か さ た な は ま や ら わ — voiced,
// semi-voiced and small kana fold into their base row, ん lands in the わ row); Latin keys bucket
// A–Z after normalizing diacritics; everything else (kanji without a reading, digits, symbols)
// falls into ＃. Section order: kana rows first, then A–Z, then ＃.

const val OTHER_SECTION = "#"

// Row leader → every hiragana character belonging to that row.
private val KANA_ROWS = listOf(
    'あ' to "ぁあぃいぅうぇえぉおゔ",
    'か' to "かがきぎくぐけげこごゕゖ",
    'さ' to "さざしじすずせぜそぞ",
    'た' to "ただちぢっつづてでとど",
    'な' to "なにぬねの",
    'は' to "はばぱひびぴふぶぷへべぺほぼぽ",
    'ま' to "まみむめも",
    'や' to "ゃやゅゆょよ",
    'ら' to "らりるれろ",
    'わ' to "ゎわゐゑをん",
)

private const val LATIN_SECTION_COUNT = 26

private fun katakanaToHiragana(c: Char): Char = if (c in 'ァ'..'ヶ') c - 0x60 else c

/** Katakana folded to hiragana, for kana-insensitive comparison and matching. */
fun foldKana(s: String): String = buildString(s.length) { s.forEach { append(katakanaToHiragana(it)) } }

private fun kanaRowLeader(c: Char): Char? = KANA_ROWS.firstOrNull { (_, members) -> c in members }?.first

/** The section title a sort key buckets under: a gojūon row leader, an A–Z letter, or ＃. */
fun sectionTitleForSortKey(key: String): String {
    val first = key.trim().firstOrNull() ?: return OTHER_SECTION
    kanaRowLeader(katakanaToHiragana(first))?.let { return it.toString() }
    val normalized = first.toString().normalizeString().uppercase(Locale.ROOT).firstOrNull()
    return if (normalized != null && normalized in 'A'..'Z') normalized.toString() else OTHER_SECTION
}

/** Ordering rank of a section title: kana rows, then A–Z, then ＃. */
fun sectionRank(title: String): Int {
    val c = title.firstOrNull() ?: return KANA_ROWS.size + LATIN_SECTION_COUNT
    val kanaIndex = KANA_ROWS.indexOfFirst { it.first == c }
    return when {
        kanaIndex >= 0 -> kanaIndex
        c in 'A'..'Z' -> KANA_ROWS.size + (c - 'A')
        else -> KANA_ROWS.size + LATIN_SECTION_COUNT
    }
}
