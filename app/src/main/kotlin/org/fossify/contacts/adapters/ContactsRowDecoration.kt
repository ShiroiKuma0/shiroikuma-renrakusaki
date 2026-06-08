package org.fossify.contacts.adapters

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// Draws the configurable spacing and dividers on the main contact lists, for both the single-column list
// and the 2–4 column grid. Between rows: a gap of max(spacing, horizontal-divider) with the horizontal
// divider centered in it. Between columns: a gap of max(spacing, vertical-divider) with the vertical
// divider centered. With columns == 1 the column logic is inert, so this also covers plain list view.
class ContactsRowDecoration(
    private val columns: Int,
    spacingPx: Int,
    private val hDividerPx: Int,
    hDividerColor: Int,
    private val vDividerPx: Int,
    vDividerColor: Int,
) : RecyclerView.ItemDecoration() {
    private val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = hDividerColor
        style = Paint.Style.FILL
    }
    private val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = vDividerColor
        style = Paint.Style.FILL
    }

    private val rowGap = maxOf(spacingPx, hDividerPx)
    private val colGap = maxOf(spacingPx, vDividerPx)

    private fun rowCount(itemCount: Int) = (itemCount + columns - 1) / columns

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val column = position % columns
        val isLastRow = position >= (rowCount(parent.adapter?.itemCount ?: 0) - 1) * columns

        // Distribute the column gap so every column keeps an equal content width (no gap at the outer edges).
        if (columns > 1) {
            outRect.left = column * colGap / columns
            outRect.right = colGap - (column + 1) * colGap / columns
        }
        outRect.bottom = if (isLastRow) 0 else rowGap
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (hDividerPx <= 0 && vDividerPx <= 0) {
            return
        }
        val rows = rowCount(parent.adapter?.itemCount ?: 0)
        val left = parent.paddingLeft
        val contentWidth = parent.width - parent.paddingLeft - parent.paddingRight

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) {
                continue
            }
            val column = position % columns
            val isLastRow = position >= (rows - 1) * columns

            if (hDividerPx > 0 && !isLastRow) {
                val top = child.bottom + child.translationY + (rowGap - hDividerPx) / 2f
                canvas.drawRect(left.toFloat(), top, (left + contentWidth).toFloat(), top + hDividerPx, hPaint)
            }

            if (vDividerPx > 0 && columns > 1 && column < columns - 1) {
                val centerX = left + (column + 1) * contentWidth / columns
                val x = centerX - vDividerPx / 2f
                // Extend through the row gap (when present) so the column line reads as continuous.
                val bottom = if (isLastRow) child.bottom.toFloat() else (child.bottom + rowGap).toFloat()
                canvas.drawRect(x, child.top.toFloat(), x + vDividerPx, bottom, vPaint)
            }
        }
    }
}
