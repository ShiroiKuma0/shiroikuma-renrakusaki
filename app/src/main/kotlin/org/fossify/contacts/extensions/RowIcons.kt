package org.fossify.contacts.extensions

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import org.fossify.contacts.R
import org.fossify.commons.extensions.getColoredDrawableWithColor
import kotlin.math.max
import kotlin.math.roundToInt

// The icon of a row on the UI page: sized by the row's label, not fixed.
private const val ICON_PER_TEXT = 1.25f
private const val ICON_FLOOR_DP = 18f
private const val GAP_PER_ICON = 0.5f

/** Draws [inner] moved down or up by [offsetY], so that it can sit on the label's first line. */
private class FirstLineIcon(private val inner: Drawable) : Drawable() {
    var offsetY = 0f

    fun resize(size: Int) {
        inner.setBounds(0, 0, size, size)
        setBounds(0, 0, size, size)
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top + offsetY)
        inner.draw(canvas)
        canvas.restore()
    }

    override fun getIntrinsicWidth() = bounds.width()
    override fun getIntrinsicHeight() = bounds.height()
    override fun setAlpha(alpha: Int) {
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** The width the icon and its gap take before the label's text — where a line beneath the label starts. */
fun TextView.labelIconGutter(): Int {
    val icon = compoundDrawablesRelative[0] ?: return 0
    return icon.bounds.width() + compoundDrawablePadding
}

/**
 * Puts the icon [res] before this label, in [color]: 1.25 × the label's text size and never under
 * 18 dp, its gap growing with it, centred on the label's first line. The size is read again at every
 * layout, so a font pass that runs after the row is built is followed; [onGutter] hears the new
 * width of icon + gap each time it changes, for whatever is aligned under the label's text.
 */
fun TextView.setLabelIcon(@DrawableRes res: Int, color: Int, onGutter: ((gutter: Int) -> Unit)? = null) {
    val icon = FirstLineIcon(resources.getColoredDrawableWithColor(res, color))
    val floor = ICON_FLOOR_DP * resources.displayMetrics.density

    fun resizeIfNeeded(): Boolean {
        val size = max(textSize * ICON_PER_TEXT, floor).roundToInt()
        if (icon.bounds.width() == size && compoundDrawablesRelative[0] === icon) return false
        icon.resize(size)
        compoundDrawablePadding = (size * GAP_PER_ICON).roundToInt()
        setCompoundDrawablesRelative(icon, null, null, null)
        onGutter?.invoke(labelIconGutter())
        return true
    }

    fun centreOnFirstLine() {
        val lines = layout ?: return
        val region = height - compoundPaddingTop - compoundPaddingBottom
        val textTop = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> max(0, region - lines.height) / 2f
            Gravity.BOTTOM -> max(0, region - lines.height).toFloat()
            else -> 0f
        }
        val offset = textTop + (lines.getLineTop(0) + lines.getLineBottom(0)) / 2f - region / 2f
        if (offset != icon.offsetY) {
            icon.offsetY = offset
            invalidate()
        }
    }

    (getTag(R.id.row_icon_sizer) as? View.OnLayoutChangeListener)?.let { removeOnLayoutChangeListener(it) }
    val sizer = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        // a new size is set after this pass, never from inside it; the next pass then centres it
        if (compoundDrawablesRelative[0] !== icon) return@OnLayoutChangeListener
        val size = max(textSize * ICON_PER_TEXT, floor).roundToInt()
        if (icon.bounds.width() != size) post { resizeIfNeeded() } else centreOnFirstLine()
    }
    setTag(R.id.row_icon_sizer, sizer)
    addOnLayoutChangeListener(sizer)
    resizeIfNeeded()
}
