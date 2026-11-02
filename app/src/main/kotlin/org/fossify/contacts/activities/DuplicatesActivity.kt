package org.fossify.contacts.activities

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.provider.ContactsContract.CommonDataKinds
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyTextView
import org.fossify.contacts.R
import org.fossify.contacts.databinding.ActivityThemeBinding
import org.fossify.contacts.databinding.ItemThemeSectionBinding
import org.fossify.contacts.databinding.ItemThemeSubgroupBinding
import org.fossify.contacts.extensions.applyTopBarColors
import org.fossify.contacts.extensions.pill
import org.fossify.contacts.extensions.pillRow
import org.fossify.contacts.helpers.DuplicateCleaner

/**
 * Clean up contacts stored more than once (helpers/DuplicateCleaner): remove exact copies in one go,
 * then go through the groups whose entries differ — merge them into one, keep one, or mark them as
 * different contacts. Every deletion is preceded by a vCard backup of what it touches.
 */
class DuplicatesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private var scan: DuplicateCleaner.Scan? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
        binding.themeToolbar.title = getString(R.string.dup_title)
    }

    override fun onResume() {
        super.onResume()
        // A solid accent bar at every scroll position: the scroll-tinted bar is black at the top, where the
        // title — coloured to contrast the accent — vanished into it.
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow, topBarColor = getProperPrimaryColor())
        applyTopBarColors(binding.themeAppbar)
        if (scan == null) rescan()
    }

    private fun rescan() {
        showMessage(getString(R.string.dup_scanning))
        ensureBackgroundThread {
            val result = DuplicateCleaner.scan(applicationContext)
            runOnUiThread {
                scan = result
                render(result)
            }
        }
    }

    // Run [action] off the main thread, then rescan — the screen always shows what is stored now.
    private fun run(action: () -> Unit) {
        if (busy) return
        busy = true
        showMessage(getString(R.string.dup_working))
        ensureBackgroundThread {
            try {
                action()
            } catch (e: Exception) {
                runOnUiThread { toast(org.fossify.commons.R.string.unknown_error_occurred) }
            }
            runOnUiThread {
                busy = false
                rescan()
            }
        }
    }

    private fun render(scan: DuplicateCleaner.Scan) {
        val holder = binding.themeHolder
        holder.removeAllViews()

        section(getString(R.string.dup_exact))
        if (scan.exactCopies == 0) {
            text(getString(R.string.dup_exact_none))
        } else {
            text(getString(R.string.dup_exact_summary, scan.exactCopies, scan.contactsWithCopies))
            holder.addView(pillRow(pill(getString(R.string.dup_exact_remove, scan.exactCopies), main = true) {
                ConfirmationDialog(
                    this,
                    message = getString(R.string.dup_exact_confirm, scan.exactCopies, backupPath()),
                    positive = org.fossify.commons.R.string.yes,
                    negative = org.fossify.commons.R.string.no,
                ) {
                    run {
                        val removed = DuplicateCleaner.removeExactCopies(applicationContext, scan)
                        runOnUiThread { toast(getString(R.string.dup_removed, removed)) }
                    }
                }
            }, startPaddingPx = margin()))
        }

        val review = scan.toReview
        section(getString(R.string.dup_review, review.size))
        text(getString(if (review.isEmpty()) R.string.dup_review_none else R.string.dup_review_hint))
        review.forEach { group -> renderGroup(group) }

        text(getString(R.string.dup_backups_at, backupPath()), secondary = true)
    }

    // One group per card: its name, then each entry — a header line, its fields, and its two actions.
    private fun renderGroup(group: DuplicateCleaner.Group) {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = CARD_RADIUS_DP * density
                setStroke((CARD_STROKE_DP * density).toInt(), getProperPrimaryColor().adjustAlpha(CARD_STROKE_ALPHA))
            }
            val pad = (CARD_PAD_DP * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(margin(), (CARD_GAP_DP * density).toInt(), margin(), (CARD_GAP_DP * density).toInt())
            }
        }
        binding.themeHolder.addView(card)

        card.addView(label(group.name, sizeSp = GROUP_TITLE_SP, color = getProperPrimaryColor(), bold = true))
        // What sets the entries apart, always spelled out — even when it is only where they are stored.
        card.addView(label(differenceSummary(group.distinct), sizeSp = ENTRY_HEADER_SP, color = getProperPrimaryColor(), topDp = 2f))
        val common = group.distinct.map { e -> linesOf(e).map { it.second }.toSet() }.reduce { a, b -> a intersect b }
        val stores = group.distinct.map { storeOf(it) }.toSet()
        group.distinct.forEachIndexed { index, entry ->
            val copies = group.clusters[index].copies.size
            val header = entryHeader(entry, index + 1, copies)
            // The storage is a difference too: mark the header when the entries are not all in one place.
            val headerText = if (stores.size > 1) "≠ $header" else header
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(label(headerText, sizeSp = ENTRY_HEADER_SP, color = getProperTextColor(), bold = true, topDp = 10f))
            texts.addView(label(describe(entry, common), sizeSp = ENTRY_BODY_SP, color = getProperTextColor()))
            row.addView(texts)
            card.addView(row)
            // The entry's own picture, large on the right; a tap shows it whole.
            if (DuplicateCleaner.hasPhoto(entry)) addThumbnail(row, entry)
            card.addView(pillRow(
                pill(getString(R.string.dup_merge_into, index + 1), main = true) {
                    mergeWithPhotoChoice(entry, group)
                },
                pill(getString(R.string.dup_keep_only, index + 1)) {
                    run { DuplicateCleaner.keepOnly(applicationContext, entry, group) }
                },
            ))
        }
        card.addView(pillRow(
            pill(getString(R.string.dup_not_duplicates)) {
                run { DuplicateCleaner.markNotDuplicates(applicationContext, group) }
            },
            // A contact not wanted at all: every entry goes (after a backup), once confirmed.
            pill(getString(R.string.dup_delete_all)) {
                ConfirmationDialog(
                    this,
                    message = getString(R.string.dup_delete_all_confirm, group.name, group.clusters.sumOf { it.copies.size + 1 }),
                    positive = org.fossify.commons.R.string.yes,
                    negative = org.fossify.commons.R.string.no,
                ) {
                    run { DuplicateCleaner.deleteAll(applicationContext, group) }
                }
            },
        ))
    }

    private fun addThumbnail(row: LinearLayout, entry: Contact) {
        val size = (THUMB_DP * resources.displayMetrics.density).toInt()
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
                marginStart = (12 * resources.displayMetrics.density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 12 * resources.displayMetrics.density
                setStroke((1.5f * resources.displayMetrics.density).toInt(), getProperPrimaryColor().adjustAlpha(CARD_STROKE_ALPHA))
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12 * resources.displayMetrics.density)
                }
            }
        }
        row.addView(image)
        ensureBackgroundThread {
            val bitmap = DuplicateCleaner.loadPhoto(applicationContext, entry)
            runOnUiThread {
                if (bitmap == null) {
                    image.visibility = View.GONE
                } else {
                    image.setImageBitmap(bitmap)
                    image.setOnClickListener { showPhoto(bitmap) }
                }
            }
        }
    }

    // Merge — and when the entries carry different pictures, first ask which one the merged contact keeps.
    private fun mergeWithPhotoChoice(target: Contact, group: DuplicateCleaner.Group) {
        val withPhoto = group.distinct.withIndex().filter { DuplicateCleaner.hasPhoto(it.value) }
        val distinctPhotos = withPhoto.distinctBy { DuplicateCleaner.photoKey(it.value) }
        if (distinctPhotos.size < 2) {
            run { DuplicateCleaner.mergeInto(applicationContext, target, group) }
            return
        }

        val density = resources.displayMetrics.density
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        var dialog: androidx.appcompat.app.AlertDialog? = null
        distinctPhotos.forEach { (index, entry) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams((PICK_DP * density).toInt(), (PICK_DP * density).toInt())
            }
            row.addView(image)
            row.addView(pill(getString(R.string.dup_keep_photo_of, index + 1)) {
                dialog?.dismiss()
                run { DuplicateCleaner.mergeInto(applicationContext, target, group, photoFrom = entry) }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = (16 * density).toInt()
                }
            })
            holder.addView(row)
            ensureBackgroundThread {
                val bitmap = DuplicateCleaner.loadPhoto(applicationContext, entry)
                runOnUiThread { image.setImageBitmap(bitmap) }
            }
        }
        getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { setupDialogStuff(holder, this, R.string.dup_which_photo) { dialog = it } }
    }

    private fun showPhoto(bitmap: android.graphics.Bitmap) {
        val view = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply { setupDialogStuff(view, this) }
    }

    private fun entryHeader(c: Contact, number: Int, copies: Int): String {
        val store = if (c.isPrivate()) getString(R.string.visibility_private) else getString(R.string.visibility_system) + " · " + c.source
        return "$number · $store" + if (copies > 0) " (+$copies ${getString(R.string.dup_copies)})" else ""
    }

    // Everything an entry holds, one line per field, so two entries can be compared at a glance.
    // Everything an entry holds, one line per field, each tagged with the kind of field it is — so the
    // lines all entries share can be told apart from the ones that make them differ.
    private fun linesOf(c: Contact): List<Pair<Int, String>> {
        val lines = ArrayList<Pair<Int, String>>()
        val nameParts = listOf(c.prefix, c.firstName, c.middleName, c.surname, c.suffix).filter { it.isNotEmpty() }
        if (nameParts.isNotEmpty()) lines.add(R.string.dup_kind_name to getString(R.string.dup_name_parts) + ": " + nameParts.joinToString(" | "))
        if (c.extra.hasPhonetic()) {
            lines.add(R.string.dup_kind_reading to listOf(c.extra.phoneticFamily, c.extra.phoneticMiddle, c.extra.phoneticGiven).filter { it.isNotEmpty() }.joinToString(" "))
        }
        if (c.nickname.isNotEmpty()) lines.add(R.string.dup_kind_nickname to c.nickname)
        c.phoneNumbers.forEach { lines.add(R.string.dup_kind_phones to "☎ ${it.value}") }
        c.emails.forEach { lines.add(R.string.dup_kind_emails to "✉ ${it.value}") }
        c.addresses.forEach { lines.add(R.string.dup_kind_addresses to "⌂ ${it.value.replace('\n', ' ')}") }
        c.IMs.forEach { lines.add(R.string.dup_kind_ims to "💬 ${it.value}") }
        c.events.forEach { lines.add(R.string.dup_kind_events to "📅 ${it.value}" + if (it.label.isNotEmpty()) " (${it.label})" else "") }
        c.websites.forEach { lines.add(R.string.dup_kind_websites to "🔗 $it") }
        if (c.organization.isNotEmpty()) {
            lines.add(R.string.dup_kind_org to "🏢 " + listOf(c.organization.company, c.organization.jobPosition).filter { it.isNotEmpty() }.joinToString(", "))
        }
        c.extra.relations.forEach { lines.add(R.string.dup_kind_relations to "👤 ${it.name} (${CommonDataKinds.Relation.getTypeLabel(resources, it.type, it.label)})") }
        c.extra.sipAddresses.forEach { lines.add(R.string.dup_kind_sip to "☎ sip:${it.value}") }
        if (c.notes.isNotEmpty()) lines.add(R.string.dup_kind_notes to "📝 ${c.notes.replace('\n', ' ')}")
        if (c.groups.isNotEmpty()) lines.add(R.string.dup_kind_groups to "👥 " + c.groups.joinToString(", ") { it.title })
        if (c.starred == 1) lines.add(R.string.dup_kind_star to "★")
        if (DuplicateCleaner.hasPhoto(c)) lines.add(R.string.dup_kind_photo to "🖼 " + getString(R.string.dup_has_photo) + PHOTO_KEY_MARK + DuplicateCleaner.photoKey(c))
        return lines
    }

    private fun storeOf(c: Contact) = if (c.isPrivate()) "private" else "system:${c.source}"

    /** "Differs in: storage, phone numbers" — or that the entries hold the very same content. */
    private fun differenceSummary(entries: List<Contact>): String {
        val all = entries.map { linesOf(it) }
        val common = all.map { lines -> lines.map { it.second }.toSet() }.reduce { a, b -> a intersect b }
        val kinds = LinkedHashSet<Int>()
        val parts = ArrayList<String>()
        val privacies = entries.map { it.isPrivate() }.toSet()
        val accounts = entries.filter { !it.isPrivate() }.map { it.source }.toSet()
        if (privacies.size > 1) parts.add(getString(R.string.dup_kind_storage))
        if (accounts.size > 1) parts.add(getString(R.string.dup_kind_account, accounts.joinToString(" / ")))
        all.flatten().filter { it.second !in common }.forEach { kinds.add(it.first) }
        parts.addAll(kinds.map { getString(it) })
        return if (parts.isEmpty()) {
            getString(R.string.dup_differs_nothing)
        } else {
            getString(R.string.dup_differs_in, parts.joinToString(", "))
        }
    }

    // An entry's lines: shared ones dim, differing ones in full colour, bold and marked ≠.
    private fun describe(c: Contact, common: Set<String>): CharSequence {
        val text = getProperTextColor()
        val out = SpannableStringBuilder()
        linesOf(c).forEachIndexed { i, (_, line) ->
            if (i > 0) out.append("\n")
            val start = out.length
            val shown = line.substringBefore(PHOTO_KEY_MARK)
            if (line in common) {
                out.append(shown)
                out.setSpan(ForegroundColorSpan(text.adjustAlpha(SECONDARY_ALPHA)), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                out.append("≠ ").append(shown)
                out.setSpan(ForegroundColorSpan(getProperPrimaryColor()), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return out
    }

    private fun backupPath() = DuplicateCleaner.backupDir(this)?.absolutePath.orEmpty()

    // ---- building blocks ----------------------------------------------------------------------------

    private fun showMessage(message: String) {
        binding.themeHolder.removeAllViews()
        text(message)
    }

    private fun section(title: String) {
        val b = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeSectionLabel.text = title
        b.themeSectionLabel.setTextColor(getProperPrimaryColor())
        b.themeSectionRule.setBackgroundColor(getProperPrimaryColor())
        binding.themeHolder.addView(b.root)
    }

    private fun subgroup(title: String) {
        val b = ItemThemeSubgroupBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeSubgroupLabel.text = title
        b.themeSubgroupLabel.setTextColor(getProperPrimaryColor())
        b.themeSubgroupRule.setBackgroundColor(getProperPrimaryColor())
        binding.themeHolder.addView(b.root)
    }

    private fun margin() = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)

    private fun text(value: String, secondary: Boolean = false) {
        val color = if (secondary) getProperTextColor().adjustAlpha(SECONDARY_ALPHA) else getProperTextColor()
        binding.themeHolder.addView(label(value, sizeSp = if (secondary) ENTRY_BODY_SP else BODY_SP, color = color).apply {
            setPadding(margin(), margin() / 2, margin(), margin() / 2)
        })
    }

    private fun label(value: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false, topDp: Float = 0f) = MyTextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (topDp * resources.displayMetrics.density).toInt(), 0, 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    companion object {
        private const val BODY_SP = 16f
        private const val GROUP_TITLE_SP = 20f
        private const val ENTRY_HEADER_SP = 16f
        private const val ENTRY_BODY_SP = 14f
        private const val SECONDARY_ALPHA = 0.75f
        private const val CARD_RADIUS_DP = 14f
        private const val CARD_STROKE_DP = 1.5f
        private const val CARD_STROKE_ALPHA = 0.6f
        private const val CARD_PAD_DP = 14f
        private const val CARD_GAP_DP = 8f
        private const val THUMB_DP = 104f
        private const val PICK_DP = 120f
        // Separates a line's text from its hidden comparison key (the photo fingerprint).
        private const val PHOTO_KEY_MARK = "\u0000"
    }
}
