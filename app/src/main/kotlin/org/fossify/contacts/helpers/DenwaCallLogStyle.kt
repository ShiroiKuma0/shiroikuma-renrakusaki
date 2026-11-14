package org.fossify.contacts.helpers

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import org.fossify.commons.extensions.CALL_DURATION_JAPANESE
import org.fossify.commons.extensions.CALL_TIME_JAPANESE
import org.fossify.contacts.extensions.getInstalledDialerAppPackage
import java.io.File

// The look of denwa's call log, borrowed for the 発着信履歴 section of the contact screen. 白い熊 wants
// that section to look exactly like denwa's Recents, settings included, so nothing here is a setting
// of ours: denwa serves its resolved colours, fonts, line weights and formats from its provider
// `<denwa id>.calllogstyle` (`/style` as key/value rows, `/font/<file>` for an imported font file). An
// older denwa without the provider leaves us on its defaults, which are the values below.

private const val STYLE_AUTHORITY_SUFFIX = ".calllogstyle"
private const val MISSED_DEFAULT = 0xFFD32F2F.toInt() // md_red_500_dark
private const val OUTGOING_DEFAULT = 0xFF43A047.toInt() // md_green_600
private const val INCOMING_DEFAULT = 0xFF1E88E5.toInt() // md_blue_600
private const val SIM1_DEFAULT = 0xFFFF0000.toInt()
private const val SIM2_DEFAULT = 0xFF0000FF.toInt()
private const val DAY_HEADER_SCALE_DEFAULT = 0.76f
private const val SEMIBOLD_WEIGHT = 600
private const val BOLD_WEIGHT = 700
const val DENWA_MONOSPACE_FONT = "@monospace"

/** One text role of the call log: its stored font family, weight (0 = default) and size (sp, 0 = unset). */
data class DenwaFont(val family: String = "", val weight: Int = 0, val sizeSp: Int = 0)

data class DenwaCallLogStyle(
    val name: Int,
    val subtitle: Int,
    val date: Int,
    val dayDate: Int,
    val missed: Int = MISSED_DEFAULT,
    val incoming: Int = INCOMING_DEFAULT,
    val outgoing: Int = OUTGOING_DEFAULT,
    val divider: Int,
    val dayDivider: Int,
    val dateUnderline: Int,
    val simText: Int = PALETTE_YELLOW,
    val sim1: Int = SIM1_DEFAULT,
    val sim2: Int = SIM2_DEFAULT,
    val dividerDp: Int = 1,
    val dayDividerDp: Int = 4,
    val dateUnderlineDp: Int = 2,
    val nameFont: DenwaFont = DenwaFont(),
    val subtitleFont: DenwaFont = DenwaFont(),
    val dateFont: DenwaFont = DenwaFont(),
    val dayDateFont: DenwaFont = DenwaFont(),
    val textSizePx: Float = 0f,
    val dayHeaderScale: Float = DAY_HEADER_SCALE_DEFAULT,
    val simTextBold: Boolean = true,
    val timeFormat: Int = CALL_TIME_JAPANESE,
    val durationFormat: Int = CALL_DURATION_JAPANESE,
    val imperialDate: Boolean = true,
    val formatNumbers: Boolean = true,
    /** True when the values came from denwa itself rather than these defaults. */
    val fromDenwa: Boolean = false,
) {
    fun simColor(simId: Int) = if (simId == 1) sim1 else sim2
}

/**
 * Denwa's call-log look, read fresh (cheap: one small cursor). [text] / [primary] stand in for denwa's
 * own text and accent colours when the provider is missing. Call on a background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a broken or refusing provider = defaults
fun Context.loadDenwaCallLogStyle(text: Int, primary: Int): DenwaCallLogStyle {
    val defaults = DenwaCallLogStyle(
        name = text, subtitle = text, date = text, dayDate = text,
        divider = primary, dayDivider = primary, dateUnderline = primary,
    )
    val authority = denwaStyleAuthority() ?: return defaults
    val values = HashMap<String, String>()
    try {
        contentResolver.query(Uri.parse("content://$authority/style"), null, null, null, null)?.use { cursor ->
            val keyIndex = cursor.getColumnIndex("key")
            val valueIndex = cursor.getColumnIndex("value")
            if (keyIndex < 0 || valueIndex < 0) return defaults
            while (cursor.moveToNext()) {
                values[cursor.getString(keyIndex).orEmpty()] = cursor.getString(valueIndex).orEmpty()
            }
        }
    } catch (e: Exception) {
        return defaults
    }
    if (values.isEmpty()) return defaults

    fun color(key: String, fallback: Int) = values["color.$key"]?.toLongOrNull()?.toInt() ?: fallback
    fun int(key: String, fallback: Int) = values[key]?.toIntOrNull() ?: fallback
    fun flag(key: String, fallback: Boolean) = values[key]?.let { it == "1" || it == "true" } ?: fallback
    fun font(slot: String) = DenwaFont(
        family = values["font.$slot.family"].orEmpty(),
        weight = int("font.$slot.weight", 0),
        sizeSp = int("font.$slot.size", 0),
    )

    return DenwaCallLogStyle(
        name = color("name", defaults.name),
        subtitle = color("subtitle", defaults.subtitle),
        date = color("date", defaults.date),
        dayDate = color("day_date", defaults.dayDate),
        missed = color("missed", defaults.missed),
        incoming = color("incoming", defaults.incoming),
        outgoing = color("outgoing", defaults.outgoing),
        divider = color("divider", defaults.divider),
        dayDivider = color("day_divider", defaults.dayDivider),
        dateUnderline = color("date_underline", defaults.dateUnderline),
        simText = color("sim_text", defaults.simText),
        sim1 = color("sim1", defaults.sim1),
        sim2 = color("sim2", defaults.sim2),
        dividerDp = int("thickness.divider", defaults.dividerDp),
        dayDividerDp = int("thickness.day_divider", defaults.dayDividerDp),
        dateUnderlineDp = int("thickness.date_underline", defaults.dateUnderlineDp),
        nameFont = font("name"),
        subtitleFont = font("subtitle"),
        dateFont = font("date"),
        dayDateFont = font("day_date"),
        textSizePx = values["text_size_px"]?.toFloatOrNull() ?: 0f,
        dayHeaderScale = values["day_header_scale"]?.toFloatOrNull() ?: defaults.dayHeaderScale,
        simTextBold = flag("sim_text_bold", defaults.simTextBold),
        timeFormat = int("time_format", defaults.timeFormat),
        durationFormat = int("duration_format", defaults.durationFormat),
        imperialDate = flag("imperial_date", defaults.imperialDate),
        formatNumbers = flag("format_numbers", defaults.formatNumbers),
        fromDenwa = true,
    )
}

private fun Context.denwaStyleAuthority(): String? = getInstalledDialerAppPackage()?.let { it + STYLE_AUTHORITY_SUFFIX }

/**
 * The typeface for one of denwa's text roles — its own imported font file, copied once into our cache
 * (keyed by name) because Typeface needs a path or an asset, not a stream. Call on a background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a missing or corrupt font falls back to default
fun Context.denwaTypeface(font: DenwaFont, baseStyle: Int = Typeface.NORMAL): Typeface {
    val base = when {
        font.family.isEmpty() -> Typeface.DEFAULT
        font.family == DENWA_MONOSPACE_FONT -> Typeface.MONOSPACE
        else -> denwaFontFile(font.family)?.let {
            try {
                Typeface.createFromFile(it)
            } catch (e: Exception) {
                null
            }
        } ?: Typeface.DEFAULT
    }
    if (font.weight <= 0) {
        return Typeface.create(base, baseStyle)
    }
    val bold = baseStyle == Typeface.BOLD || baseStyle == Typeface.BOLD_ITALIC
    val italic = baseStyle == Typeface.ITALIC || baseStyle == Typeface.BOLD_ITALIC
    val weight = if (bold) maxOf(font.weight, BOLD_WEIGHT) else font.weight
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, italic)
    } else {
        Typeface.create(base, if (weight >= SEMIBOLD_WEIGHT) Typeface.BOLD else Typeface.NORMAL)
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Context.denwaFontFile(name: String): File? {
    if (name.contains('/') || name.contains("..")) return null
    val dir = File(cacheDir, "denwa-fonts").apply { mkdirs() }
    val file = File(dir, name)
    if (file.length() > 0) return file
    val authority = denwaStyleAuthority() ?: return null
    return try {
        contentResolver.openInputStream(Uri.parse("content://$authority/font/${Uri.encode(name)}"))?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        file.takeIf { it.length() > 0 }
    } catch (e: Exception) {
        file.delete()
        null
    }
}
