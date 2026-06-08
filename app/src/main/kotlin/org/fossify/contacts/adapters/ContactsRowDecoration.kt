package org.fossify.contacts.adapters

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// Draws the configurable inter-row spacing and divider on the main contact lists. The gap reserved below
// each row (except the last) is max(spacing, divider); the divider line, if any, is centered in that gap.
class ContactsRowDecoration(
    private val spacingPx: Int,
    private val dividerPx: Int,
    dividerColor: Int,
) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dividerColor
        style = Paint.Style.FILL
    }

    private val gap = maxOf(spacingPx, dividerPx)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        val lastPosition = (parent.adapter?.itemCount ?: 0) - 1
        outRect.bottom = if (position == RecyclerView.NO_POSITION || position == lastPosition) 0 else gap
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (dividerPx <= 0) {
            return
        }
        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()
        val lastPosition = (parent.adapter?.itemCount ?: 0) - 1
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position == lastPosition) {
                continue
            }
            val top = child.bottom + child.translationY + (gap - dividerPx) / 2f
            canvas.drawRect(left, top, right, top + dividerPx, paint)
        }
    }
}
