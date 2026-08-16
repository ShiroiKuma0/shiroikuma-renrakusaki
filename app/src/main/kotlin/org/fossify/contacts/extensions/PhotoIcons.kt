package org.fossify.contacts.extensions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.extensions.applyColorFilter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Fork: the contact screens draw their icons straight on top of the contact photo — the top bar's
// icons (back arrow, edit, share, delete, ⋮) and the actions on the photo's bottom edge (favorite,
// call, SMS, email, change photo). On the default palette a photo-less contact is a solid accent-
// colored placeholder, so an accent-colored icon vanishes into it. Every one of those icons is
// therefore drawn as a halo in ThemeSlot.PHOTO_ICON_OUTLINE plus the glyph in its own slot color
// (PHOTO_TOOLBAR_ICON / PHOTO_ACTION_ICON), with the halo width from config.photoIconOutlineThickness.

// How many copies of the glyph are stamped around the center to form the halo. 16 is dense enough
// that neighbouring stamps overlap at every halo width we allow, so the ring reads as a solid edge.
private const val HALO_STEPS = 16

/**
 * A haloed icon. It keeps the [source] it was built from, so running an icon through
 * [photoOverlayIcon] again re-renders from the original instead of stacking a second halo on top of
 * the first — the screens re-apply their colors on every resume.
 */
private class OutlinedIcon(context: Context, bitmap: Bitmap, val source: Drawable) :
    BitmapDrawable(context.resources, bitmap)

/** [icon] painted in [fillColor] over a halo in the outline slot's color. */
fun Context.photoOverlayIcon(icon: Drawable?, fillColor: Int): Drawable? {
    val source = (icon as? OutlinedIcon)?.source ?: icon ?: return null
    val width = source.intrinsicWidth
    val height = source.intrinsicHeight
    if (width <= 0 || height <= 0) {
        // Nothing to rasterize (a color drawable, say) — the fill color is all we can honour.
        return source.apply { applyColorFilter(fillColor) }
    }

    // Drawing a copy keeps the source pristine for the next re-render; the halo grows the icon
    // outwards, which leaves the glyph itself at its original size wherever the host doesn't scale.
    val glyph = source.constantState?.newDrawable()?.mutate() ?: source
    val halo = (config.photoIconOutlineThickness * resources.displayMetrics.density).roundToInt()
    val bitmap = createBitmap(width + halo * 2, height + halo * 2)
    val canvas = Canvas(bitmap)

    if (halo > 0) {
        glyph.applyColorFilter(themeColor(ThemeSlot.PHOTO_ICON_OUTLINE))
        repeat(HALO_STEPS) { step ->
            val angle = 2 * PI * step / HALO_STEPS
            val left = halo + (cos(angle) * halo).roundToInt()
            val top = halo + (sin(angle) * halo).roundToInt()
            glyph.setBounds(left, top, left + width, top + height)
            glyph.draw(canvas)
        }
    }

    glyph.applyColorFilter(fillColor)
    glyph.setBounds(halo, halo, halo + width, halo + height)
    glyph.draw(canvas)
    return OutlinedIcon(this, bitmap, source)
}

/**
 * Repaint every icon a contact screen draws over the photo: [toolbar]'s navigation, menu and overflow
 * icons in the top-bar slot, [actionIcons] (the row above the photo's bottom edge) in the action slot.
 * Idempotent, so screens can call it on every resume.
 */
fun Context.applyPhotoOverlayIcons(toolbar: MaterialToolbar, vararg actionIcons: ImageView) {
    val toolbarColor = themeColor(ThemeSlot.PHOTO_TOOLBAR_ICON)
    // Assigning null would clear an icon rather than leave it alone, so only set what we could repaint.
    photoOverlayIcon(toolbar.navigationIcon, toolbarColor)?.let { toolbar.navigationIcon = it }
    photoOverlayIcon(toolbar.overflowIcon, toolbarColor)?.let { toolbar.overflowIcon = it }
    val menu = toolbar.menu
    for (index in 0 until menu.size()) {
        val item = menu.getItem(index)
        item.icon = photoOverlayIcon(item.icon, toolbarColor)
    }

    val actionColor = themeColor(ThemeSlot.PHOTO_ACTION_ICON)
    actionIcons.forEach { it.setImageDrawable(photoOverlayIcon(it.drawable, actionColor)) }
}
