package org.fossify.contacts.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.databinding.ItemContactsListFieldBinding
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.helpers.RowField
import org.fossify.contacts.helpers.RowFieldEntry
import org.fossify.commons.R as CR
import java.util.Collections

// The "order box": tick which contact fields show in the list, drag to reorder, and use the chevrons to
// merge a field onto the previous shown line (▶, a column to its right) or split it back to its own line (◀).
// Every change persists via [onChanged]; the editor lives embedded in ThemeActivity's "Contacts' list" section.
class ContactsListFieldsAdapter(
    private val activity: SimpleActivity,
    private val entries: MutableList<RowFieldEntry>,
    private val onChanged: () -> Unit,
) : RecyclerView.Adapter<ContactsListFieldsAdapter.ViewHolder>() {

    private val textColor = activity.getProperTextColor()
    private val accentColor = activity.getProperPrimaryColor()
    private val backgroundColor = activity.getProperBackgroundColor()
    private val touchHelper = ItemTouchHelper(DragCallback())

    private companion object {
        const val DISABLED_ALPHA = 0.3f
        val NAME_KEY_PARTS = listOf("name", "prefix", "suffix")
    }

    fun attachTo(recyclerView: RecyclerView) {
        touchHelper.attachToRecyclerView(recyclerView)
    }

    inner class ViewHolder(val binding: ItemContactsListFieldBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactsListFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = entries.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val hasShownBefore = entries.take(position).any { it.checked }
        val canMoveRight = entry.checked && hasShownBefore && !entry.sameLine
        val canMoveLeft = entry.checked && entry.sameLine

        holder.binding.apply {
            fieldCheckbox.text = activity.getString(entry.field.labelRes)
            fieldCheckbox.setColors(textColor, accentColor, backgroundColor)
            fieldCheckbox.setOnCheckedChangeListener(null)
            // the row's icon, coloured by what the field is about (the menu icon roles)
            val (icon, role) = when {
                entry.field == RowField.PHONE -> CR.drawable.ic_phone_vector to ThemeSlot.MENU_ICON_CALL
                entry.field == RowField.EMAIL -> CR.drawable.ic_mail_vector to ThemeSlot.MENU_ICON_MESSAGE
                entry.field == RowField.IM -> CR.drawable.ic_sms_vector to ThemeSlot.MENU_ICON_MESSAGE
                NAME_KEY_PARTS.any { it in entry.field.key } -> CR.drawable.ic_person_vector to ThemeSlot.MENU_ICON_CONTACT
                else -> CR.drawable.ic_label_vector to ThemeSlot.MENU_ICON_VIEW
            }
            fieldIcon.setImageResource(icon)
            fieldIcon.applyColorFilter(activity.themeColor(role))
            fieldCheckbox.isChecked = entry.checked
            fieldCheckbox.setOnCheckedChangeListener { _, isChecked ->
                entry.checked = isChecked
                if (!isChecked) {
                    entry.sameLine = false
                }
                persist()
            }

            setupButton(fieldMoveLeft, canMoveLeft) {
                entry.sameLine = false
                persist()
            }
            setupButton(fieldMoveRight, canMoveRight) {
                entry.sameLine = true
                persist()
            }

            fieldDragHandle.applyColorFilter(textColor)
            fieldDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                }
                false
            }
        }
    }

    private fun setupButton(button: android.widget.ImageView, enabled: Boolean, action: () -> Unit) {
        button.applyColorFilter(textColor)
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
        button.isEnabled = enabled
        button.setOnClickListener { if (enabled) action() }
    }

    // The first shown field always starts a line; keep the stored config consistent with that.
    private fun normalizeFirstShown() {
        entries.firstOrNull { it.checked }?.sameLine = false
    }

    // The button enabled-states depend on the whole list (which field is shown first, what precedes each),
    // so a single edit can change several rows — rebind them all.
    @SuppressLint("NotifyDataSetChanged")
    private fun persist() {
        normalizeFirstShown()
        onChanged()
        notifyDataSetChanged()
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) {
                return false
            }
            Collections.swap(entries, from, to)
            notifyItemMoved(from, to)
            return true
        }

        @Suppress("EmptyFunctionBlock") // swipe-to-dismiss is intentionally disabled (drag-only).
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            persist()
        }
    }
}
