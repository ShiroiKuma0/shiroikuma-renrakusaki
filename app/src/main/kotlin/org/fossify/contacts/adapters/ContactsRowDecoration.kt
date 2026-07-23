package org.fossify.contacts.adapters

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

// Where a contact row sits within its (section's) grid: its column, whether it is on the section's
// first row (grouped mode only — those get the row gap + horizontal divider above them, below the
// header), whether it is on the section's last row, and whether the closing full-width section line
// draws beneath it (only the last row of an unfolded section at the very end of the list — elsewhere
// the next header's top divider closes the section). Section headers have no cell (null), so they get
// neither offsets nor dividers.
data class RowCell(
    val column: Int,
    val lastRow: Boolean,
    val firstRow: Boolean = false,
    val sectionClose: Boolean = false,
)

// Draws the configurable spacing and dividers on the main contact lists, for both the single-column list
// and the 2–4 column grid. Between rows: a gap of max(spacing, horizontal-divider) with the horizontal
// divider centered in it. Between columns: a gap of max(spacing, vertical-divider) with the vertical
// divider centered. Cell geometry comes from [cellInfo] so grouped mode (full-width headers, per-section
// column flow) and the flat list share the same drawing; with columns == 1 the column logic is inert.
class ContactsRowDecoration(
    private val columns: Int,
    spacingPx: Int,
    private val hDividerPx: Int,
    hDividerColor: Int,
    private val vDividerPx: Int,
    vDividerColor: Int,
    private val sectionLinePx: Int,
    sectionLineColor: Int,
    private val cellInfo: (position: Int) -> RowCell?,
) : RecyclerView.ItemDecoration() {
    private val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = hDividerColor
        style = Paint.Style.FILL
    }
    private val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = vDividerColor
        style = Paint.Style.FILL
    }
    private val sPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sectionLineColor
        style = Paint.Style.FILL
    }

    private val rowGap = maxOf(spacingPx, hDividerPx)
    private val colGap = maxOf(spacingPx, vDividerPx)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val cell = cellInfo(position) ?: return

        // Distribute the column gap so every column keeps an equal content width (no gap at the outer edges).
        if (columns > 1) {
            outRect.left = cell.column * colGap / columns
            outRect.right = colGap - (cell.column + 1) * colGap / columns
        }
        // A section's first row keeps the same row rhythm against its header as rows have between them.
        outRect.top = if (cell.firstRow) rowGap else 0
        outRect.bottom = when {
            cell.sectionClose -> sectionLinePx
            cell.lastRow -> 0
            else -> rowGap
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (hDividerPx <= 0 && vDividerPx <= 0 && sectionLinePx <= 0) {
            return
        }
        val left = parent.paddingLeft
        val contentWidth = parent.width - parent.paddingLeft - parent.paddingRight

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) {
                continue
            }
            val cell = cellInfo(position) ?: continue

            if (hDividerPx > 0 && !cell.lastRow) {
                val top = child.bottom + child.translationY + (rowGap - hDividerPx) / 2f
                canvas.drawRect(left.toFloat(), top, (left + contentWidth).toFloat(), top + hDividerPx, hPaint)
            }

            // The divider above a section's first row, in the gap below the header.
            if (hDividerPx > 0 && cell.firstRow) {
                val top = child.top + child.translationY - (rowGap + hDividerPx) / 2f
                canvas.drawRect(left.toFloat(), top, (left + contentWidth).toFloat(), top + hDividerPx, hPaint)
            }

            if (vDividerPx > 0 && columns > 1 && cell.column < columns - 1) {
                val centerX = left + (cell.column + 1) * contentWidth / columns
                val x = centerX - vDividerPx / 2f
                // Extend through the row gaps (when present) so the column line reads as continuous.
                val top = if (cell.firstRow) (child.top - rowGap).toFloat() else child.top.toFloat()
                val bottom = if (cell.lastRow) child.bottom.toFloat() else (child.bottom + rowGap).toFloat()
                canvas.drawRect(x, top, x + vDividerPx, bottom, vPaint)
            }

            // Closing full-width line under the final unfolded section (same drawing may repeat per
            // column child of the last row — the rects coincide, so it stays a single line).
            if (sectionLinePx > 0 && cell.sectionClose) {
                val top = child.bottom + child.translationY
                canvas.drawRect(left.toFloat(), top, (left + contentWidth).toFloat(), top + sectionLinePx, sPaint)
            }
        }
    }
}
