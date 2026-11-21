package org.fossify.contacts.activities

import org.fossify.contacts.extensions.resetAllThemeColors
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.commons.helpers.UiRoleSection
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.extensions.circleDrawable
import org.fossify.commons.extensions.rippleDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.os.Environment
import android.provider.Settings
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.ChipGroup
import org.fossify.commons.R as CR
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.pillDrawable
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.contacts.R
import org.fossify.contacts.extensions.getOwnContacts
import org.fossify.contacts.adapters.ContactsListFieldsAdapter
import org.fossify.contacts.databinding.ActivityThemeBinding
import org.fossify.contacts.databinding.DialogColumnSpacerBinding
import org.fossify.contacts.databinding.DialogExportImportBinding
import org.fossify.contacts.databinding.DialogPatternInputBinding
import org.fossify.contacts.databinding.ItemThemeActionBinding
import org.fossify.contacts.databinding.ItemThemeColorBinding
import org.fossify.contacts.databinding.ItemThemeFieldsOrderBinding
import org.fossify.contacts.databinding.ItemThemeSliderBinding
import org.fossify.contacts.databinding.ItemThemeSubgroupBinding
import org.fossify.contacts.databinding.ItemThemeSwitchBinding
import org.fossify.contacts.databinding.ItemThemeTextBinding
import org.fossify.contacts.databinding.ItemThemeThumbnailBinding
import org.fossify.contacts.databinding.ItemThemeTokenBinding
import org.fossify.contacts.databinding.ItemThemeValueBinding
import org.fossify.contacts.dialogs.AlphaColorPickerDialog
import org.fossify.contacts.dialogs.ExportContactsDialog
import org.fossify.contacts.dialogs.FontPickerDialog
import org.fossify.contacts.dialogs.pickRinAccount
import org.fossify.contacts.helpers.ROUTE_OFF
import org.fossify.contacts.helpers.ROUTE_RIN
import org.fossify.contacts.helpers.ROUTE_RIN_PREFIX
import org.fossify.contacts.helpers.ROUTE_SIM1
import org.fossify.contacts.helpers.ROUTE_SIM2
import org.fossify.contacts.helpers.MenuPills
import org.fossify.contacts.helpers.Rindenwa
import org.fossify.contacts.helpers.pickerLabel
import org.fossify.contacts.helpers.rinBadge
import org.fossify.contacts.extensions.tryImportContactsFromFile
import org.fossify.contacts.helpers.SettingsExport
import org.fossify.commons.views.MyTextView
import org.fossify.contacts.helpers.DuplicateCleaner
import org.fossify.contacts.helpers.SystemStorage
import org.fossify.contacts.helpers.SystemMirror
import org.fossify.contacts.helpers.VcfExporter
import org.fossify.contacts.extensions.FontWeightOption
import org.fossify.contacts.extensions.ThemeGroup
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.helpers.ContactsListConfig
import org.fossify.contacts.helpers.DEFAULT_PRIVATE_DATA_APPS
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_DIVIDER_DP
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_SPACING_DP
import org.fossify.contacts.helpers.DETAIL_PATTERN_OLDER_PREFIX
import org.fossify.contacts.helpers.DETAIL_PATTERN_TODAY_PREFIX
import org.fossify.contacts.helpers.DETAIL_PATTERN_YEAR_PREFIX
import org.fossify.contacts.helpers.MAX_PHOTO_ICON_OUTLINE_DP
import org.fossify.contacts.helpers.MAX_SECTION_PADDING_DP
import org.fossify.contacts.helpers.TIME_FORMAT_12H
import org.fossify.contacts.helpers.TIME_FORMAT_24H
import org.fossify.contacts.helpers.TIME_FORMAT_JAPANESE
import org.fossify.contacts.helpers.TIME_FORMAT_SYSTEM
import org.fossify.contacts.helpers.applyDetailPattern
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_THUMBNAIL_DP
import org.fossify.contacts.helpers.MIN_CONTACTS_LIST_THUMBNAIL_DP
import org.fossify.contacts.helpers.RowFieldEntry
import org.fossify.contacts.extensions.applyTopBarColors
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.fontDisplayName
import org.fossify.contacts.extensions.importFont
import org.fossify.contacts.extensions.resetThemeColor
import org.fossify.contacts.extensions.setThemeColor
import org.fossify.contacts.extensions.showFontSample
import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.helpers.MAX_FONT_SIZE_SP
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Each cascade level is indented one more step: section contents one step in, subgroup contents two.
private const val INDENT_STEP_DP = 72

// Sample-timestamp ages for the pattern previews: safely "earlier this year"-ish and "older".
private const val SAMPLE_THIS_YEAR_DAYS = 30
private const val SAMPLE_OLDER_DAYS = 400

// The page indents in 72dp steps; the narrower Export / Import panel steps in this instead.
private const val DIALOG_INDENT_STEP_DP = 24

// Vertical padding for every row, replacing the commons style's 20dp — every row is only as tall as its
// content (black-yellow theme, "Row padding — minimal, every row").
private const val ROW_PADDING_VERTICAL_DP = 2
private const val SECTION_GAP_DP = 10
private const val SECTION_HEADER_PAD_DP = 8
private const val SECTION_UNDERLINE_DP = 2
private const val SECTION_UNDERLINE_GAP_DP = 2
private const val SECTION_CHEVRON_SP = 22f

// The chip index under the app-settings pill.
private const val INDEX_GAP_DP = 6
private const val INDEX_TEXT_SP = 13f
private const val INDEX_PAD_H_DP = 10
private const val INDEX_PAD_V_DP = 5
private const val INDEX_DRAGGED_ALPHA = 0.3f

// A row's icon, before its label.
private const val ROW_ICON_DP = 20
private const val ROW_ICON_GAP_DP = 10
private val CONTACT_CALL_HISTORY_COUNTS = listOf(3, 5, 10, 20, 50)

// How many hex characters of the automation token stay visible at each end.
private const val TOKEN_ABBREVIATION_EDGE = 8

// Every translation the app ships (mirrors the res/values-* locale dirs, plus English), as BCP-47 tags.
// Offered by the "Application language" picker; display names render in each language itself.
private val APP_LANGUAGE_TAGS = listOf(
    "ar", "az", "be", "bg", "bn", "bn-BD", "bqi", "br", "bs", "ca", "ckb", "cr", "cs", "cy", "da", "de",
    "el", "en", "en-GB", "en-IN", "eo", "es", "es-419", "es-US", "et", "eu", "fa", "fi", "fil", "fr",
    "ga", "gl", "he", "hi-IN", "hr", "hu", "ia", "id", "is", "it", "ja", "kab", "kn", "ko-KR", "kr",
    "lt", "ltg", "lv", "mk", "ml", "ms", "my", "nb-NO", "ne", "nl", "nn", "oc", "or", "pa", "pa-PK",
    "pl", "pt", "pt-BR", "pt-PT", "ro", "ru", "sat", "si", "sk", "sl", "sr", "sv", "ta", "te", "th",
    "tr", "uk", "ur", "vi", "zgh", "zh-CN", "zh-HK", "zh-TW",
)

/** A top-level section of the page: its stored [key], its heading, its (shorter) chip label, and what builds its rows. */
private class UiSection(val key: String, val title: String, val chip: String = title, val fill: () -> Unit)

/**
 * The icon a row of the page carries, coloured by what the row does (black-yellow theme: “Every row has an
 * icon”). The colours are the menu icon roles, so a row and a menu line that do the same wear the same colour.
 */
private enum class RowIcon(val res: Int, val role: ThemeSlot) {
    SETTINGS(CR.drawable.ic_settings_cog_vector, ThemeSlot.MENU_ICON_VIEW),
    TOGGLE(CR.drawable.ic_check_circle_vector, ThemeSlot.MENU_ICON_VIEW),
    VALUE(CR.drawable.ic_edit_vector, ThemeSlot.MENU_ICON_VIEW),
    COLOR(R.drawable.ic_palette_vector, ThemeSlot.MENU_ICON_VIEW),
    TEXT(R.drawable.ic_text_fields_vector, ThemeSlot.MENU_ICON_VIEW),
    WEIGHT(R.drawable.ic_format_bold_vector, ThemeSlot.MENU_ICON_VIEW),
    SIZE(R.drawable.ic_tune_vector, ThemeSlot.MENU_ICON_VIEW),
    TIME(CR.drawable.ic_clock_vector, ThemeSlot.MENU_ICON_VIEW),
    CHECK(CR.drawable.ic_search_vector, ThemeSlot.MENU_ICON_VIEW),
    RESET(CR.drawable.ic_reset_vector, ThemeSlot.MENU_ICON_VIEW),
    SECRET(CR.drawable.ic_lock_vector, ThemeSlot.MENU_ICON_BLOCK),
    SHARE(CR.drawable.ic_share_vector, ThemeSlot.MENU_ICON_SHARE),
    DIRECTORY(CR.drawable.ic_folder_vector, ThemeSlot.MENU_ICON_SHARE),
    AUTOMATION(CR.drawable.ic_code_vector, ThemeSlot.MENU_ICON_SHARE),
    CALL(CR.drawable.ic_phone_vector, ThemeSlot.MENU_ICON_CALL),
    CALL_LOG(CR.drawable.ic_clock_vector, ThemeSlot.MENU_ICON_CALL),
    CONTACT(CR.drawable.ic_person_vector, ThemeSlot.MENU_ICON_CONTACT),
    CONTACTS(CR.drawable.ic_people_vector, ThemeSlot.MENU_ICON_CONTACT),
    APPS(CR.drawable.ic_apps_vector, ThemeSlot.MENU_ICON_CONTACT),
    MOVE(CR.drawable.ic_move_vector, ThemeSlot.MENU_ICON_CONTACT),
    SHOWN(CR.drawable.ic_unhide_vector, ThemeSlot.MENU_ICON_VIEW),
    PRIVATE(CR.drawable.ic_hide_vector, ThemeSlot.MENU_ICON_BLOCK),
}

private const val ROW_PAD_DP = 2f
private const val NOTE_TEXT_SP = 13f

@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    // Holds the per-field styling controls for the "Contacts' list" section; rebuilt when the field set changes.
    private var rowStylingContainer: LinearLayout? = null

    // Where the row builders add their rows: the page itself, or the body of the section being filled.
    private lateinit var body: ViewGroup

    // The page's sections in their shown order, and each one's views by its key.
    private var sections: List<UiSection> = emptyList()
    private val sectionWrappers = HashMap<String, View>()
    private val sectionBodies = HashMap<String, LinearLayout>()
    private val sectionChevrons = HashMap<String, TextView>()
    private val sectionsFilled = HashSet<String>()

    // Export / Import (top section): category selection survives buildRows() rebuilds; the export
    // folder (a persisted SAF tree grant) lives in a device-local prefs file that is itself never exported.
    // Seeded from the categories' own defaultOn flag — the same answer LIST_CATEGORIES sends 自由作業盤's
    // picker, so this sheet and the automation one open on the same ticks.
    private val eximSelected = SettingsExport.Item.defaultSelection.toMutableSet()
    private var ignoredExportContactSources = HashSet<String>()

    private val eximDirPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onEximDirPicked(uri)
    }

    private val eximSaveAs = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runEximExport { contentResolver.openOutputStream(uri) }
    }

    private val eximImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runEximImport(uri)
    }

    private val vcfImportPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) tryImportContactsFromFile(uri) {}
    }

    private val vcfExportCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) {
            try {
                exportContactsTo(ignoredExportContactSources, contentResolver.openOutputStream(uri))
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onFontImported(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
    }

    override fun onResume() {
        super.onResume()
        // A solid accent bar at every scroll position: the scroll-tinted bar turned black at the top, where
        // the title — coloured to contrast the accent — all but vanished.
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow)
        applyTopBarColors(binding.themeAppbar)
        buildRows()
    }

    // The page: the way to the app's own settings, the chip index, then the sections — each a folding block,
    // folded unless 白い熊 opened it, in the order 白い熊 dragged the chips into (black-yellow theme, "Every UI
    // page — a folding accordion with a draggable chip index"). A folded section's rows are not built until
    // it is first opened: the page has several hundred of them.
    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()
        sectionWrappers.clear()
        sectionBodies.clear()
        sectionChevrons.clear()
        sectionsFilled.clear()
        rowStylingContainer = null
        body = binding.themeHolder

        val primaryColor = getProperPrimaryColor()
        val stepPx = (INDENT_STEP_DP * resources.displayMetrics.density).toInt()

        // The way to the app's own settings first, as the page's one thick pill; it is not a section.
        addActionRow(
            getString(R.string.app_settings_link), getString(R.string.app_settings_link_desc), 0, main = true, icon = RowIcon.SETTINGS,
        ) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Your data first (backup, who sees private contacts, housekeeping), then the language, then the
        // look section by section. The keys are stored: never rename one.
        val built = ArrayList<UiSection>()
        built.add(UiSection("exim", getString(R.string.eim_title)) { addExportImportSection(primaryColor, stepPx) })
        built.add(UiSection("private", getString(R.string.private_contacts_section)) { addPrivateContactsSection(primaryColor, stepPx) })
        built.add(UiSection("maintenance", getString(R.string.maintenance_section)) { addMaintenanceSection(primaryColor, stepPx) })
        built.add(UiSection("language", getString(R.string.theme_app_language)) { addLanguageSection(primaryColor, stepPx) })
        built.add(UiSection("buttons", getString(R.string.buttons_section)) { addButtonsSection(primaryColor, stepPx) })
        // The roles of what the commons draws, each in the section of its item (the keys are stored too).
        fun roleSection(section: UiRoleSection) = built.add(
            UiSection("roles_" + section.name.lowercase(Locale.ROOT), getString(section.labelRes)) { addRoleRows(section, stepPx) }
        )
        listOf(UiRoleSection.TEXT, UiRoleSection.FIELDS, UiRoleSection.DIALOGS, UiRoleSection.SWITCHES, UiRoleSection.MARKS)
            .forEach(::roleSection)
        ThemeGroup.entries.forEach { group ->
            built.add(UiSection("theme_" + group.name.lowercase(Locale.ROOT), getString(group.labelRes)) { addGroup(group, primaryColor, stepPx) })
            if (group == ThemeGroup.SWIPE_CALL) {
                built.add(
                    UiSection(
                        "call_history", getString(R.string.contact_call_history_settings), getString(R.string.contact_call_history_section),
                    ) { addContactCallHistorySection(primaryColor, stepPx) }
                )
            }
        }
        roleSection(UiRoleSection.BARS)
        roleSection(UiRoleSection.TOASTS)
        sections = orderedSections(built)

        addSectionIndex(primaryColor)
        sections.forEachIndexed { index, section -> addSection(section, index == 0, primaryColor) }
    }

    /** The stored order applied to [built]: stored keys first, then sections a later build added, at their built place. */
    private fun orderedSections(built: List<UiSection>): List<UiSection> {
        val byKey = built.associateBy { it.key }
        val stored = config.uiSectionOrder.split(',').mapNotNull { byKey[it] }.distinct()
        return stored + built.filter { it !in stored }
    }

    // The jump index: a wrapping grid of small outlined chips, one per section. A tap unfolds the section
    // and scrolls to it; a long-press lifts the chip, the chips reorder live under the finger, and the
    // sections follow on drop.
    private fun addSectionIndex(accent: Int) {
        val density = resources.displayMetrics.density
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val group = ChipGroup(this).apply {
            chipSpacingHorizontal = (INDEX_GAP_DP * density).toInt()
            chipSpacingVertical = (INDEX_GAP_DP * density).toInt()
            setPaddingRelative(margin, (INDEX_GAP_DP * density).toInt(), margin, (INDEX_GAP_DP * density).toInt())
        }
        val background = getProperBackgroundColor()
        sections.forEach { section ->
            group.addView(TextView(this).apply {
                text = section.chip
                tag = section.key
                setTextColor(accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, INDEX_TEXT_SP)
                val h = (INDEX_PAD_H_DP * density).toInt()
                val v = (INDEX_PAD_V_DP * density).toInt()
                setPadding(h, v, h, v)
                this.background = pillDrawable(accent, background, main = false)
                setOnClickListener { jumpToSection(section.key) }
                setOnLongClickListener { chip ->
                    chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    chip.startDragAndDrop(null, View.DragShadowBuilder(chip), chip, 0)
                    chip.alpha = INDEX_DRAGGED_ALPHA
                    true
                }
            })
        }

        val orderBefore = sections.map { it.key }
        // The chip last moved past: the same chip is not swapped again until the finger has been over the
        // dragged one, or two chips of unequal width would swap back and forth under a still finger.
        var lastTarget: View? = null
        group.setOnDragListener { _, event ->
            val dragged = event.localState as? View
            if (dragged == null || dragged.parent !== group) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val target = (0 until group.childCount).map { group.getChildAt(it) }
                        .firstOrNull { event.x >= it.left && event.x < it.right && event.y >= it.top && event.y < it.bottom }
                    if (target === dragged) {
                        lastTarget = null
                    } else if (target != null && target !== lastTarget) {
                        val to = group.indexOfChild(target)
                        group.removeView(dragged)
                        group.addView(dragged, to)
                        lastTarget = target
                    }
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    dragged.alpha = 1f
                    val order = (0 until group.childCount).map { group.getChildAt(it).tag as String }
                    if (order != orderBefore) {
                        config.uiSectionOrder = order.joinToString(",")
                        // not from inside the drag dispatch: the rebuild removes the view being dispatched to
                        group.post { buildRows() }
                    }
                }
            }
            true
        }
        binding.themeHolder.addView(group)
    }

    // A folding section: the 1 px darker-accent line above it (every section but the first; it stays whether
    // the section is folded or open), its bold heading underlined as wide as its text with › folded / ⌄ open
    // at the right, and its rows in a body that folds away.
    private fun addSection(section: UiSection, first: Boolean, accent: Int) {
        val density = resources.displayMetrics.density
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!first) {
            wrapper.addView(
                View(this).apply { setBackgroundColor(MenuPills.divider(this@ThemeActivity)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = (SECTION_GAP_DP * density).toInt() },
            )
        }

        val label = TextView(this).apply {
            text = section.title
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(org.fossify.commons.R.dimen.bigger_text_size))
            setTypeface(typeface, Typeface.BOLD)
        }
        val labelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // as wide as the column, which is as wide as the label's text
            addView(
                View(this@ThemeActivity).apply { setBackgroundColor(accent) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (SECTION_UNDERLINE_DP * density).toInt())
                    .apply { topMargin = (SECTION_UNDERLINE_GAP_DP * density).toInt() },
            )
        }
        val chevron = TextView(this).apply {
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_CHEVRON_SP)
            includeFontPadding = false
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val v = (SECTION_HEADER_PAD_DP * density).toInt()
            setPaddingRelative(margin, v, margin, v)
            background = rippleDrawable()
            addView(labelColumn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(View(this@ThemeActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(chevron)
            setOnClickListener { setSectionOpen(section.key, section.key !in config.uiSectionsOpen) }
        }
        wrapper.addView(header)

        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(inner)
        binding.themeHolder.addView(wrapper)

        sectionWrappers[section.key] = wrapper
        sectionBodies[section.key] = inner
        sectionChevrons[section.key] = chevron
        showSectionState(section.key, section.key in config.uiSectionsOpen)
    }

    private fun setSectionOpen(key: String, open: Boolean) {
        val stored = config.uiSectionsOpen
        config.uiSectionsOpen = if (open) stored + key else stored - key
        showSectionState(key, open)
    }

    private fun showSectionState(key: String, open: Boolean) {
        val inner = sectionBodies[key] ?: return
        if (open && sectionsFilled.add(key)) {
            // rows are added to `body`; it points here only while this section's own rows are built
            body = inner
            sections.firstOrNull { it.key == key }?.fill?.invoke()
            body = binding.themeHolder
        }
        inner.beVisibleIf(open)
        sectionChevrons[key]?.text = if (open) "⌄" else "›"
    }

    /** A tap on an index chip: unfold the section and scroll to it. */
    private fun jumpToSection(key: String) {
        val wrapper = sectionWrappers[key] ?: return
        setSectionOpen(key, true)
        binding.themeHolder.post { binding.themeNestedScrollview.smoothScrollTo(0, wrapper.top) }
    }

    // ---- Export / Import (kojiki-style: persisted folder, last-export line, category checklist) ----

    private fun addExportImportSection(primaryColor: Int, stepPx: Int) {

        // Two rows only, as in the sister apps: where exports go, and the panel that does the work.
        // Everything selectable — categories, their sub-options, the VCF one-shots — lives in the panel.
        addValueRow(getString(R.string.eim_dir), eximDir()?.name ?: getString(R.string.eim_dir_unset), stepPx, icon = RowIcon.DIRECTORY) {
            eximDirPicker.launch(eximDirUri())
        }
        addActionRow(getString(R.string.eim_open), lastExportSummary(), stepPx, main = true, icon = RowIcon.SHARE) { showExportImportDialog() }

        // Automation sits directly below the export rows it drives — the placement every sister app
        // shares, so 白い熊 finds it where backup lives.
        addAutomationSubgroup(primaryColor, stepPx)
    }

    /** The opener row's second line: when the newest export in the folder was written. */
    private fun lastExportSummary(): String {
        val last = lastExportText()
        return if (last == getString(R.string.eim_none)) {
            getString(R.string.eim_last_never)
        } else {
            getString(R.string.eim_last_at, last)
        }
    }

    /**
     * The Export / Import panel: the category checklist (sub-options indented under their parent, and
     * following its toggle), the one-shot VCF actions, and Export / Import / Cancel. The page's own
     * section stays two rows deep — this is where the choosing happens.
     */
    private fun showExportImportDialog() {
        val view = DialogExportImportBinding.inflate(layoutInflater)
        val holder = view.exportImportHolder
        view.exportImportHint.text = getString(R.string.eim_hint)
        view.exportImportHint.setTextColor(themeColor(ThemeSlot.SETTINGS_NOTE))
        val primaryColor = getProperPrimaryColor()
        val stepPx = (DIALOG_INDENT_STEP_DP * resources.displayMetrics.density).toInt()

        val catRows = HashMap<SettingsExport.Item, ItemThemeSwitchBinding>()
        addSwitchRow(
            title = getString(R.string.eim_select_all),
            checked = eximSelected.containsAll(SettingsExport.Item.entries),
            indent = 0,
            parent = holder,
        ) { on ->
            SettingsExport.Item.entries.forEach { item ->
                if (on) eximSelected.add(item) else eximSelected.remove(item)
                catRows[item]?.themeSwitch?.isChecked = on
            }
        }
        for (item in SettingsExport.Item.entries.filter { it.isTopLevel }) {
            catRows[item] = addSwitchRow(getString(item.labelRes), item in eximSelected, stepPx, parent = holder) { on ->
                if (on) eximSelected.add(item) else eximSelected.remove(item)
                item.children.forEach { child ->
                    if (on) eximSelected.add(child) else eximSelected.remove(child)
                    catRows[child]?.themeSwitch?.isChecked = on
                }
            }
            for (child in item.children) {
                val label = getString(child.labelRes)
                catRows[child] = addSwitchRow(label, child in eximSelected, stepPx * 2, parent = holder) { on ->
                    if (on) eximSelected.add(child) else eximSelected.remove(child)
                }
            }
        }

        var dialog: AlertDialog? = null
        addSubgroupHeader(getString(R.string.eim_vcf_group), primaryColor, 0, holder)
        addActionRow(getString(R.string.export_contacts_to_vcf), null, stepPx, parent = holder, icon = RowIcon.SHARE) {
            dialog?.dismiss()
            startVcfExport()
        }
        addActionRow(getString(R.string.import_contacts_from_vcf), null, stepPx, parent = holder, icon = RowIcon.SHARE) {
            dialog?.dismiss()
            startVcfImport()
        }

        getAlertDialogBuilder()
            .setPositiveButton(R.string.eim_export) { _, _ -> onEximExport() }
            .setNegativeButton(R.string.eim_import) { _, _ -> onEximImport() }
            .setNeutralButton(org.fossify.commons.R.string.cancel, null)
            .apply { setupDialogStuff(view.root, this, R.string.eim_title) { dialog = it } }
    }

    // The folder itself lives in SettingsExport, so this page and the headless automation export
    // (StateExportReceiver) always resolve the same one.
    private fun eximDirUri(): Uri? = SettingsExport.configuredDirUri(this)

    private fun eximDir(): DocumentFile? = SettingsExport.configuredDir(this)

    private fun onEximDirPicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        SettingsExport.setConfiguredDirUri(this, uri)
        buildRows()
    }

    private fun lastExportText(): String {
        val dir = eximDir() ?: return getString(R.string.eim_none)
        val newest = runCatching {
            dir.listFiles().filter {
                it.isFile && it.name?.startsWith(SettingsExport.EXPORT_PREFIX) == true && it.name?.endsWith(".zip") == true
            }.maxByOrNull { it.lastModified() }
        }.getOrNull() ?: return getString(R.string.eim_none)
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(newest.lastModified()))
    }

    private fun onEximExport() {
        if (eximSelected.isEmpty()) {
            toast(R.string.eim_none_selected)
            return
        }
        val dir = eximDir()
        if (dir == null) {
            runCatching { eximSaveAs.launch(SettingsExport.exportFileName()) }
                .onFailure { toast(org.fossify.commons.R.string.no_app_found) }
        } else {
            runEximExport {
                val file = dir.createFile("application/zip", SettingsExport.exportFileName())
                    ?: error("cannot create a file in ${dir.name}")
                contentResolver.openOutputStream(file.uri)
            }
        }
    }

    private fun runEximExport(openOut: () -> OutputStream?) {
        toast(org.fossify.commons.R.string.exporting)
        SettingsExport.export(this, eximSelected.toSet(), openOut) { result ->
            runOnUiThread {
                result
                    .onSuccess {
                        toast(getString(R.string.eim_export_ok, it))
                        buildRows()
                    }
                    .onFailure { toast(getString(R.string.eim_export_fail, it.message ?: "")) }
            }
        }
    }

    private fun onEximImport() {
        if (eximSelected.isEmpty()) {
            toast(R.string.eim_none_selected)
            return
        }
        runCatching { eximImportPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            .onFailure { toast(org.fossify.commons.R.string.no_app_found) }
    }

    private fun runEximImport(uri: Uri) {
        toast(org.fossify.commons.R.string.importing)
        ensureBackgroundThread {
            val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                runOnUiThread { toast(getString(R.string.eim_import_fail, "no input stream")) }
                return@ensureBackgroundThread
            }
            SettingsExport.import(this, bytes, eximSelected.toSet()) { result ->
                runOnUiThread {
                    result
                        .onSuccess { summary ->
                            // Persistent result dialog with an explicit restart — imported colors/fonts
                            // only fully apply on a fresh process.
                            ConfirmationDialog(
                                activity = this,
                                message = getString(R.string.eim_import_done, summary),
                                positive = R.string.eim_restart_now,
                                negative = R.string.eim_restart_later
                            ) { restartApp() }
                        }
                        .onFailure { toast(getString(R.string.eim_import_fail, it.message ?: "")) }
                }
            }
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(Intent.makeRestartActivityTask(intent.component))
        Runtime.getRuntime().exit(0)
    }

    // The stock VCF export/import flows (moved here from the settings page's Migrating section).

    private fun startVcfExport() {
        ExportContactsDialog(this, config.lastExportPath, true) { file, ignoredContactSources ->
            ignoredExportContactSources = ignoredContactSources
            runCatching { vcfExportCreator.launch(file.name) }
                .onFailure { toast(org.fossify.commons.R.string.no_app_found) }
        }
    }

    private fun startVcfImport() {
        runCatching { vcfImportPicker.launch("text/x-vcard") }
            .onFailure { toast(org.fossify.commons.R.string.no_app_found) }
    }

    private fun exportContactsTo(ignoredContactSources: HashSet<String>, outputStream: OutputStream?) {
        // showOnlyContactsWithNumbers is a display filter, and commons applies it to an export too
        // whenever the ignore set is empty — i.e. exactly when every source is ticked. Left at its
        // default it silently drops every number-less contact from the file.
        this.getOwnContacts(
            getAll = true,
            gettingDuplicates = false,
            ignoredContactSources = ignoredContactSources,
            showOnlyContactsWithNumbers = false,
        ) { contacts ->
            if (contacts.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
            } else {
                VcfExporter().exportContacts(
                    context = this,
                    outputStream = outputStream,
                    contacts = contacts,
                    showExportingToast = true
                ) { result ->
                    toast(
                        when (result) {
                            VcfExporter.ExportResult.EXPORT_OK -> org.fossify.commons.R.string.exporting_successful
                            VcfExporter.ExportResult.EXPORT_PARTIAL ->
                                org.fossify.commons.R.string.exporting_some_entries_failed
                            else -> org.fossify.commons.R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    // ---- Automation: a subgroup of Export / Import, since every automation intent drives that export
    // (see StateExportReceiver and BackupContactsReceiver) ----

    private fun addAutomationSubgroup(primaryColor: Int, stepPx: Int) {
        addSubgroupHeader(getString(R.string.automation), primaryColor, stepPx)

        // Three rows now, in the order every sister app uses (contract v2 §2): the master switch — default
        // ON, since 応用管理 must be able to restore this app onto a clean phone where nothing has been
        // configured — then the switch that decides whether a token is required at all (default OFF), then
        // the token itself, shown ONLY when it is being asked for. A 48-character secret sitting under an
        // off switch invites 白い熊 to paste it somewhere it will do nothing.
        addSwitchRow(
            title = getString(R.string.enable_automation),
            icon = RowIcon.AUTOMATION,
            checked = config.automationEnabled,
            indent = stepPx * 2,
            description = getString(R.string.enable_automation_desc),
        ) {
            config.automationEnabled = it
        }

        addSwitchRow(
            title = getString(R.string.automation_require_token),
            icon = RowIcon.SECRET,
            checked = config.automationRequireToken,
            indent = stepPx * 2,
            description = getString(R.string.automation_require_token_desc),
        ) {
            config.automationRequireToken = it
            // Rebuild so the token row below appears or disappears with the switch that governs it.
            buildRows()
        }

        if (config.automationRequireToken) {
            addTokenRow(
                indent = stepPx * 2,
                token = config.automationToken,
                onCopy = {
                    // Not commons' copyToClipboard: that one toasts the value itself, which would put the
                    // full secret back on screen right after we deliberately abbreviated it.
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(getString(R.string.automation_token), config.automationToken)
                    )
                    toast(R.string.automation_token_copied)
                },
                onRegenerate = { row ->
                    ConfirmationDialog(
                        activity = this,
                        message = getString(R.string.automation_token_regenerate_warning),
                        positive = R.string.automation_token_regenerate,
                        negative = org.fossify.commons.R.string.cancel,
                    ) {
                        row.themeTokenValue.text = abbreviateToken(config.regenerateAutomationToken())
                        toast(R.string.automation_token_regenerated)
                    }
                },
            )
        }

        // All-files access: needed so an automation broadcast can write to an arbitrary absolute path
        // (e.g. 白い熊's archive folder) outside Download/Documents. API 30+ only.
        if (isRPlus()) {
            val granted = Environment.isExternalStorageManager()
            val state = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed)
            addValueRow(getString(R.string.all_files_access), state, stepPx * 2, icon = RowIcon.DIRECTORY) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (e2: Exception) {
                        showErrorToast(e2)
                    }
                }
            }
        }
    }

    // App-wide display language, independent of the phone locale. Backed by the AndroidX per-app
    // locales API: Android 13+ persists it in the system; below, the manifest's
    // AppLocalesMetadataHolderService (autoStoreLocales) restores it on launch.
    private fun addLanguageSection(primaryColor: Int, stepPx: Int) {
        val current = AppCompatDelegate.getApplicationLocales()
        val currentName = current[0]?.let { it.getDisplayName(it).replaceFirstChar { c -> c.uppercase() } }
            ?: getString(R.string.language_system_default)
        addValueRow(getString(R.string.theme_app_language), currentName, stepPx) {
            openLanguagePicker()
        }
    }

    private fun openLanguagePicker() {
        val items = ArrayList<RadioItem>()
        items.add(RadioItem(0, getString(R.string.language_system_default), ""))
        APP_LANGUAGE_TAGS
            .map { tag -> Locale.forLanguageTag(tag) }
            .sortedBy { it.getDisplayName(it).lowercase(Locale.getDefault()) }
            .forEachIndexed { index, locale ->
                val name = locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
                items.add(RadioItem(index + 1, name, locale.toLanguageTag()))
            }

        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checkedId = items.firstOrNull { (it.value as String).equals(currentTag, ignoreCase = true) }?.id
            ?: items.firstOrNull { (it.value as String).equals(currentTag.substringBefore('-'), ignoreCase = true) }?.id
            ?: 0

        RadioGroupDialog(this, items, checkedId) { value ->
            val tag = value as String
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            )
        }
    }

    // ---- Buttons: dialog buttons as pills (commons sk12) ----

    private fun addButtonsSection(primaryColor: Int, stepPx: Int) {
        addSwitchRow(
            title = getString(R.string.pill_dialog_buttons),
            checked = config.pillDialogButtons,
            indent = stepPx,
            description = getString(R.string.pill_dialog_buttons_desc),
        ) {
            config.pillDialogButtons = it
        }
        addRoleRows(UiRoleSection.BUTTONS, stepPx)
    }

    // ---- Colour roles of what the commons draws ----

    // One swatch row per role of [section] that no slot of this app already shows under its own label.
    private fun addRoleRows(section: UiRoleSection, stepPx: Int) {
        val shownAsSlots = ThemeSlot.entries.filter { it != ThemeSlot.SWITCH_OFF }.mapNotNull { it.role }.toSet()
        if (section == UiRoleSection.BARS) addNote(getString(R.string.system_bar_role_note), stepPx)
        UiRole.entries.filter { it.section == section && it !in shownAsSlots }.forEach { addRoleRow(it, stepPx) }
    }

    private fun addNote(text: String, indent: Int) {
        val note = TextView(this).apply {
            this.text = text
            setTextColor(themeColor(ThemeSlot.SETTINGS_NOTE))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, NOTE_TEXT_SP)
            val pad = (ROW_PAD_DP * resources.displayMetrics.density).toInt()
            setPaddingRelative(indent + rowIconInset(), pad, indent, pad)
        }
        body.addView(note)
    }

    private fun addRoleRow(role: UiRole, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(role.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = circleDrawable(uiColor(role))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, uiColor(role), addDefaultColorButton = true) { wasPositive, color ->
                if (wasPositive) setUiColor(role, color) else resetUiColor(role)
                // a role may be the default of others, and the page itself is drawn with several of them
                recreate()
            }
        }
        indentRow(row.root, indent)
        body.addView(row.root)
    }

    // ---- Maintenance: housekeeping tools ----

    private fun addMaintenanceSection(primaryColor: Int, stepPx: Int) {
        addActionRow(getString(R.string.dup_title), getString(R.string.dup_row_desc), stepPx, main = true, icon = RowIcon.CONTACTS) {
            startActivity(Intent(this, DuplicatesActivity::class.java))
        }

        // What is still outside private storage, checked live: the status is the explanation under the
        // pill that checks it again.
        addSubgroupHeader(getString(R.string.system_storage), primaryColor, stepPx)
        lateinit var check: ItemThemeActionBinding
        fun refreshStatus() {
            setActionDescription(check, getString(R.string.system_storage_checking))
            ensureBackgroundThread {
                val st = SystemStorage.status(applicationContext)
                val text = systemStorageText(st)
                runOnUiThread { setActionDescription(check, text) }
            }
        }
        check = addActionRow(getString(R.string.system_storage_check), null, stepPx * 2, opens = false, icon = RowIcon.CHECK) { refreshStatus() }
        refreshStatus()

        // Acts in place after its confirmation, so no ›.
        addActionRow(getString(R.string.all_private), getString(R.string.all_private_desc), stepPx * 2, opens = false, icon = RowIcon.MOVE) {
            confirmMoveAllToPrivate()
        }

        addSwitchRow(
            title = getString(R.string.new_contacts_private),
            icon = RowIcon.PRIVATE,
            checked = config.newContactsPrivate,
            indent = stepPx * 2,
            description = getString(R.string.new_contacts_private_desc),
        ) { config.newContactsPrivate = it }
        addSwitchRow(
            title = getString(R.string.auto_keep_clean),
            icon = RowIcon.PRIVATE,
            checked = config.autoKeepClean,
            indent = stepPx * 2,
            description = getString(R.string.auto_keep_clean_desc),
        ) { config.autoKeepClean = it }

        // Acts in place: the page is rebuilt in its original order. No ›.
        addActionRow(
            getString(R.string.ui_reset_section_order), getString(R.string.ui_reset_section_order_desc), stepPx,
            opens = false, icon = RowIcon.RESET,
        ) {
            config.uiSectionOrder = ""
            buildRows()
        }

        // Every colour role back on its default, after a red confirmation: it cannot be undone.
        addActionRow(
            getString(R.string.reset_all_colours), getString(R.string.reset_all_colours_desc), stepPx,
            opens = false, icon = RowIcon.RESET,
        ) {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.reset_all_colours_confirm),
                positive = org.fossify.commons.R.string.yes,
                negative = org.fossify.commons.R.string.no,
                destructive = true,
            ) {
                resetAllThemeColors()
                config.contactsListRevision += 1
                recreate()
            }
        }
    }

    private fun systemStorageText(st: SystemStorage.Status): String {
        if (st.isClean) return getString(R.string.system_storage_clean)
        val parts = ArrayList<String>()
        st.byAccount.forEach { (account, n) -> parts.add("${account.ifEmpty { getString(R.string.phone_storage) }}: $n") }
        st.simCards.forEach { (sub, n) -> parts.add(getString(R.string.system_storage_sim, sub, n)) }
        if (st.pendingDeletes > 0) parts.add(getString(R.string.system_storage_pending, st.pendingDeletes))
        return getString(R.string.system_storage_left, parts.joinToString(" · "))
    }

    private fun confirmMoveAllToPrivate() {
        ConfirmationDialog(
            activity = this,
            message = getString(R.string.all_private_confirm, DuplicateCleaner.backupDir(this)?.absolutePath.orEmpty()),
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no,
        ) { runMoveAllToPrivate() }
    }

    // Counts up while it works; the screen stays on and the dialog cannot be dismissed half-way.
    private fun runMoveAllToPrivate() {
        val text = MyTextView(this).apply {
            text = getString(R.string.all_private_working, 0, 0)
            setTextColor(getProperTextColor())
            textSize = 18f
            val pad = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            setPadding(pad, pad, pad, pad)
        }
        var dialog: AlertDialog? = null
        getAlertDialogBuilder().setCancelable(false).apply {
            setupDialogStuff(text, this, R.string.all_private, cancelOnTouchOutside = false) { dialog = it }
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureBackgroundThread {
            val (moved, failed) = SystemStorage.moveEverythingToPrivate(applicationContext) { done, total ->
                runOnUiThread { text.text = getString(R.string.all_private_working, done, total) }
            }
            runOnUiThread {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                dialog?.dismiss()
                toast(getString(R.string.all_private_done, moved, failed))
                buildRows() // the status line counts again
            }
        }
    }

    // ---- Private contacts: which apps may read them through our provider ----

    private fun addPrivateContactsSection(primaryColor: Int, stepPx: Int) {
        val allowed = config.privateDataApps
        val summary = if (allowed.isEmpty()) {
            getString(R.string.private_data_apps_none)
        } else {
            allowed.map { appLabel(it) ?: it }.sortedBy { it.lowercase(Locale.getDefault()) }.joinToString(", ")
        }
        addValueRow(getString(R.string.private_data_apps), summary, stepPx, icon = RowIcon.APPS) { openPrivateDataAppsPicker() }

        addSwitchRow(
            title = getString(R.string.show_visibility_badges),
            icon = RowIcon.SHOWN,
            checked = config.showVisibilityBadges,
            indent = stepPx,
            description = getString(R.string.show_visibility_badges_desc),
        ) {
            config.showVisibilityBadges = it
        }
        addSwitchRow(
            title = getString(R.string.long_press_photo_visibility),
            icon = RowIcon.SHOWN,
            checked = config.longPressPhotoVisibility,
            indent = stepPx,
            description = getString(R.string.long_press_photo_visibility_desc),
        ) {
            config.longPressPhotoVisibility = it
        }
        addSwitchRow(
            title = getString(R.string.show_visibility_row),
            icon = RowIcon.SHOWN,
            checked = config.showVisibilityRow,
            indent = stepPx,
            description = getString(R.string.show_visibility_row_desc),
        ) {
            config.showVisibilityRow = it
        }

        // "Visible to other apps" copies (helpers/SystemMirror): where they go, and what they carry.
        addSubgroupHeader(getString(R.string.visible_copies), primaryColor, stepPx)
        val accountRow = addValueRow(getString(R.string.visible_copies_account), "…", stepPx * 2, icon = RowIcon.CONTACT) { openMirrorAccountPicker() }
        ensureBackgroundThread {
            val (name, type) = SystemMirror.targetAccount(this)
            val label = accountLabel(name, type)
            runOnUiThread { accountRow.themeValueValue.text = label }
        }
        addSwitchRow(
            title = getString(R.string.visible_copies_photo),
            icon = RowIcon.SHOWN,
            checked = config.mirrorPhoto,
            indent = stepPx * 2,
            description = getString(R.string.visible_copies_photo_desc),
        ) {
            config.mirrorPhoto = it
            ensureBackgroundThread { SystemMirror.refreshAll(applicationContext) }
        }
    }

    private fun accountLabel(name: String?, type: String?): String = when {
        name.isNullOrEmpty() -> getString(R.string.visible_copies_account_device)
        type == SystemMirror.HUAWEI_PHONE_ACCOUNT_TYPE -> getString(R.string.visible_copies_account_phone, name)
        else -> "$name ($type)"
    }

    private fun openMirrorAccountPicker() {
        ensureBackgroundThread {
            val sources = ContactsHelper(this).getDeviceContactSources().filter { it.name.isNotEmpty() && it.type.isNotEmpty() }
            runOnUiThread {
                val items = ArrayList<RadioItem>()
                items.add(RadioItem(0, getString(R.string.visible_copies_account_auto), ""))
                sources.forEachIndexed { index, source ->
                    items.add(RadioItem(index + 1, accountLabel(source.name, source.type), source.name))
                }
                val checked = items.firstOrNull { it.value == config.mirrorAccount }?.id ?: 0
                RadioGroupDialog(this, items, checked) { value ->
                    val chosen = value as String
                    if (chosen == config.mirrorAccount) return@RadioGroupDialog
                    config.mirrorAccount = chosen
                    ensureBackgroundThread {
                        SystemMirror.moveAllToTargetAccount(applicationContext)
                        runOnUiThread { buildRows() }
                    }
                }
            }
        }
    }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    // Every launchable app, the allowed ones first; an allowed app that is not installed stays listed
    // (by package name) so it can still be removed.
    private fun openPrivateDataAppsPicker() {
        val selected = config.privateDataApps.toMutableSet()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .filter { it != packageName }
            .toSet()
        val candidates = (installed + selected + DEFAULT_PRIVATE_DATA_APPS)
            .map { pkg -> pkg to appLabel(pkg) }
            .sortedWith(compareBy({ it.first !in selected }, { (it.second ?: it.first).lowercase(Locale.getDefault()) }))

        val view = DialogExportImportBinding.inflate(layoutInflater)
        view.exportImportHint.text = getString(R.string.private_data_apps_hint)
        view.exportImportHint.setTextColor(themeColor(ThemeSlot.SETTINGS_NOTE))
        candidates.forEach { (pkg, label) ->
            val title = label ?: getString(R.string.private_data_app_not_installed, pkg)
            addSwitchRow(title, pkg in selected, 0, description = pkg, explained = false, parent = view.exportImportHolder) { on ->
                if (on) selected.add(pkg) else selected.remove(pkg)
            }
        }

        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                config.privateDataApps = selected.toSet()
                buildRows()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { setupDialogStuff(view.root, this, R.string.private_data_apps) }
    }

    private fun addGroup(group: ThemeGroup, primaryColor: Int, stepPx: Int) {
        if (group == ThemeGroup.SECTIONS) {
            addSectionsSection(primaryColor, stepPx)
            return
        }
        if (group == ThemeGroup.ROWS) {
            addRowsSection(primaryColor, stepPx)
            return
        }
        if (group == ThemeGroup.PHOTO_ICONS) {
            addPhotoIconsSection(primaryColor, stepPx)
            return
        }
        if (group == ThemeGroup.SWIPE_CALL) {
            addSwipeCallSection(primaryColor, stepPx)
            return
        }
        if (group == ThemeGroup.ROUTES) {
            addRoutesSection(primaryColor, stepPx)
            return
        }


        var sawAny = false
        var lastSubgroup: Int? = null
        // the switch-off colour sits with the other switch roles, in Switches
        ThemeSlot.entries.filter { it.group == group && it != ThemeSlot.SWITCH_OFF }.forEach { slot ->
            val subgroup = slot.subgroupLabelRes
            if (!sawAny || subgroup != lastSubgroup) {
                sawAny = true
                lastSubgroup = subgroup
                // a subgroup header is part of the section's contents → one step in
                subgroup?.let { addSubgroupHeader(getString(it), primaryColor, stepPx) }
            }
            // section's direct controls indent one step; a subgroup's controls indent two
            val indent = if (subgroup != null) stepPx * 2 else stepPx
            if (slot.hasFont) addTextSlot(slot, indent, stepPx) else addColorRow(slot, indent)
        }

        if (group == ThemeGroup.MENUS) addRoleRows(UiRoleSection.MENUS, stepPx)
        if (group == ThemeGroup.LISTS) addRoleRows(UiRoleSection.LISTS, stepPx)
        if (group == ThemeGroup.TABS) {
            addSwitchRow(
                title = getString(R.string.show_groups_page),
            icon = RowIcon.CONTACTS,
                checked = config.showGroupsPage,
                indent = stepPx,
                description = getString(R.string.show_groups_page_desc),
            ) {
                config.showGroupsPage = it
            }
        }
    }

    // "Call routes": the 1 / 2 / 臨N badges on the contact screen and the 臨電話 account calls go out through.
    private fun addRoutesSection(primaryColor: Int, stepPx: Int) {
        addSwitchRow(
            title = getString(R.string.route_badges_enable),
            icon = RowIcon.CALL,
            checked = config.routeBadges,
            indent = stepPx,
            description = getString(R.string.route_badges_desc),
        ) {
            config.routeBadges = it
        }
        var row: ItemThemeValueBinding? = null
        row = addValueRow(getString(R.string.rin_default_account), rinDefaultAccountLabel(), stepPx, icon = RowIcon.CALL) {
            pickRinAccount(config.rinDefaultAccount, getString(R.string.rin_account_as_selected)) { account ->
                config.rinDefaultAccount = account
                row?.themeValueValue?.text = rinDefaultAccountLabel()
            }
        }
        ThemeSlot.entries.filter { it.group == ThemeGroup.ROUTES }.forEach { addColorRow(it, stepPx) }
    }

    private fun rinDefaultAccountLabel(): String {
        val account = config.rinDefaultAccount
        return if (account < 0) getString(R.string.rin_account_as_selected) else rinBadge(account)
    }

    // What a swipe side dials through: SIM 1, SIM 2, the 臨 default, one 臨 account, or nothing.
    private fun routeLabel(route: String): String = when {
        route == ROUTE_SIM1 -> getString(R.string.sim_slot_1)
        route == ROUTE_SIM2 -> getString(R.string.sim_slot_2)
        route == ROUTE_RIN -> getString(R.string.route_rin_default)
        route.startsWith(ROUTE_RIN_PREFIX) -> {
            val number = route.removePrefix(ROUTE_RIN_PREFIX).toIntOrNull() ?: -1
            Rindenwa.cachedAccounts(this).firstOrNull { it.number == number }?.pickerLabel() ?: rinBadge(number)
        }
        else -> getString(R.string.route_off)
    }

    private fun addSwipeRouteRow(titleRes: Int, stepPx: Int, get: () -> String, set: (String) -> Unit) {
        var row: ItemThemeValueBinding? = null
        row = addValueRow(getString(titleRes), routeLabel(get()), stepPx, icon = RowIcon.CALL) {
            val appContext = applicationContext
            ensureBackgroundThread {
                if (Rindenwa.isInstalled(appContext)) Rindenwa.refreshAccounts(appContext)
                runOnUiThread {
                    val routes = ArrayList<String>()
                    routes.add(ROUTE_SIM1)
                    routes.add(ROUTE_SIM2)
                    if (Rindenwa.isInstalled(this)) {
                        routes.add(ROUTE_RIN)
                        Rindenwa.cachedAccounts(this).filter { it.number >= 0 }.sortedBy { it.number }
                            .forEach { routes.add(ROUTE_RIN_PREFIX + it.number) }
                    }
                    if (get() !in routes && get() != ROUTE_OFF) routes.add(get())
                    routes.add(ROUTE_OFF)
                    val items = ArrayList(routes.mapIndexed { index, route -> RadioItem(index, routeLabel(route)) })
                    RadioGroupDialog(this, items, routes.indexOf(get())) { chosen ->
                        set(routes[chosen as Int])
                        config.contactsListRevision += 1 // the lists rebuild their adapters with the new routes
                        row?.themeValueValue?.text = routeLabel(get())
                    }
                }
            }
        }
    }

    // The "Contacts' list" section: the field order-box (tick / drag / column buttons) then a styling
    // control (font / weight / size / color) for each currently-shown field.
    private fun addRowsSection(primaryColor: Int, stepPx: Int) {

        addSubgroupHeader(getString(R.string.theme_rows_layout), primaryColor, stepPx)
        addSlider(
            getString(R.string.theme_rows_spacing),
            config.contactsListSpacing,
            MAX_CONTACTS_LIST_SPACING_DP,
            stepPx * 2,
        ) {
            config.contactsListSpacing = it
            config.contactsListRevision += 1
        }
        addSlider(
            getString(R.string.theme_rows_divider_thickness),
            config.contactsListDividerThickness,
            MAX_CONTACTS_LIST_DIVIDER_DP,
            stepPx * 2,
        ) {
            config.contactsListDividerThickness = it
            config.contactsListRevision += 1
        }
        addColorRow(ThemeSlot.CONTACT_DIVIDER, stepPx * 2)
        addSlider(
            getString(R.string.theme_rows_vdivider_thickness),
            config.contactsListVerticalDividerThickness,
            MAX_CONTACTS_LIST_DIVIDER_DP,
            stepPx * 2,
        ) {
            config.contactsListVerticalDividerThickness = it
            config.contactsListRevision += 1
        }
        addColorRow(ThemeSlot.CONTACT_VDIVIDER, stepPx * 2)

        addSubgroupHeader(getString(R.string.theme_rows_thumbnail_group), primaryColor, stepPx)
        addThumbnailSlider(stepPx * 2)

        addSubgroupHeader(getString(R.string.theme_rows_spacer_group), primaryColor, stepPx)
        addValueRow(getString(R.string.theme_rows_spacer_text), config.contactsListColumnSpacer, stepPx * 2) {
            editColumnSpacer()
        }
        addTextSlot(ThemeSlot.COLUMN_SPACER, stepPx * 2, stepPx)

        addSubgroupHeader(getString(R.string.theme_detail_group), primaryColor, stepPx)
        addValueRow(getString(R.string.detail_time_format), getString(detailTimeFormatLabel()), stepPx * 2, icon = RowIcon.TIME) {
            openDetailTimeFormatPicker()
        }
        // The 24-/12-hour modes expose their datetime patterns for editing, one per age bracket.
        if (config.detailTimeFormat == TIME_FORMAT_24H || config.detailTimeFormat == TIME_FORMAT_12H) {
            addPatternRow(R.string.detail_pattern_today, DETAIL_PATTERN_TODAY_PREFIX, stepPx * 2)
            addPatternRow(R.string.detail_pattern_this_year, DETAIL_PATTERN_YEAR_PREFIX, stepPx * 2)
            addPatternRow(R.string.detail_pattern_older, DETAIL_PATTERN_OLDER_PREFIX, stepPx * 2)
        }
        addTextSlot(ThemeSlot.DETAIL_CALL, stepPx * 2, stepPx)
        addTextSlot(ThemeSlot.DETAIL_SMS, stepPx * 2, stepPx)
        addColorRow(ThemeSlot.DETAIL_INCOMING, stepPx * 2)
        addColorRow(ThemeSlot.DETAIL_OUTGOING, stepPx * 2)
        addColorRow(ThemeSlot.DETAIL_MISSED, stepPx * 2)

        addSubgroupHeader(getString(R.string.theme_rows_order), primaryColor, stepPx)

        val entries = ContactsListConfig.parse(config.contactsListFields).toMutableList()
        val orderList = ItemThemeFieldsOrderBinding.inflate(layoutInflater, body, false).root
        orderList.layoutManager = LinearLayoutManager(this)
        val adapter = ContactsListFieldsAdapter(this, entries) {
            config.contactsListFields = ContactsListConfig.serialize(entries)
            config.contactsListRevision += 1
            rebuildRowStyling(entries, stepPx)
        }
        orderList.adapter = adapter
        adapter.attachTo(orderList)
        body.addView(orderList)

        rowStylingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        body.addView(rowStylingContainer)
        rebuildRowStyling(entries, stepPx)
    }

    // The "Letter sections" top-level section: the grouped-view toggle, the header letter's text styling,
    // and the underline / framing-divider lines (thickness slider + color each).
    private fun addSectionsSection(primaryColor: Int, stepPx: Int) {
        addSwitchRow(getString(R.string.theme_sections_enabled), config.contactsListGrouped, stepPx) {
            config.contactsListGrouped = it
            config.contactsListRevision += 1
        }
        addTextSlot(ThemeSlot.SECTION_HEADER, stepPx, stepPx)
        addSlider(
            getString(R.string.theme_sections_padding),
            config.contactsSectionPadding,
            MAX_SECTION_PADDING_DP,
            stepPx,
        ) {
            config.contactsSectionPadding = it
            config.contactsListRevision += 1
        }
        addSlider(
            getString(R.string.theme_sections_underline_thickness),
            config.contactsSectionUnderlineThickness,
            MAX_CONTACTS_LIST_DIVIDER_DP,
            stepPx,
        ) {
            config.contactsSectionUnderlineThickness = it
            config.contactsListRevision += 1
        }
        addColorRow(ThemeSlot.SECTION_UNDERLINE, stepPx)
        addSlider(
            getString(R.string.theme_sections_divider_thickness),
            config.contactsSectionDividerThickness,
            MAX_CONTACTS_LIST_DIVIDER_DP,
            stepPx,
        ) {
            config.contactsSectionDividerThickness = it
            config.contactsListRevision += 1
        }
        addColorRow(ThemeSlot.SECTION_DIVIDER, stepPx)
    }

    // The "Icons over the photo" section: what the contact screens' top-bar and action icons are painted
    // in, plus the halo drawn behind them so they never merge into the photo underneath.
    private fun addPhotoIconsSection(primaryColor: Int, stepPx: Int) {
        addColorRow(ThemeSlot.PHOTO_TOOLBAR_ICON, stepPx)
        addColorRow(ThemeSlot.PHOTO_ACTION_ICON, stepPx)
        addSlider(
            getString(R.string.theme_photo_outline_thickness),
            config.photoIconOutlineThickness,
            MAX_PHOTO_ICON_OUTLINE_DP,
            stepPx,
        ) {
            config.photoIconOutlineThickness = it
        }
        addColorRow(ThemeSlot.PHOTO_ICON_OUTLINE, stepPx)
        addColorRow(ThemeSlot.PHOTO_SHADE, stepPx)
        addColorRow(ThemeSlot.PHOTO_SCRIM, stepPx)
    }

    // "Swipe to call": the switch, then the colours of the band a swiped row reveals and of its marking.
    private fun addSwipeCallSection(primaryColor: Int, stepPx: Int) {
        addSwitchRow(
            title = getString(R.string.swipe_to_call_enable),
            icon = RowIcon.CALL,
            checked = config.swipeToCall,
            indent = stepPx,
            description = getString(R.string.swipe_to_call_desc),
        ) {
            config.swipeToCall = it
            config.contactsListRevision += 1 // the lists rebuild their adapters, attaching or dropping the swipe
        }
        addSwitchRow(
            title = getString(R.string.swipe_tiles_enable),
            checked = config.swipeTiles,
            indent = stepPx,
            description = getString(R.string.swipe_tiles_desc),
            icon = RowIcon.CALL,
        ) {
            config.swipeTiles = it
            config.contactsListRevision += 1
        }
        addSwipeRouteRow(R.string.swipe_left_route, stepPx, { config.swipeLeftRoute }) { config.swipeLeftRoute = it }
        addSwipeRouteRow(R.string.swipe_right_route, stepPx, { config.swipeRightRoute }) { config.swipeRightRoute = it }
        addColorRow(ThemeSlot.SWIPE_SIM1, stepPx)
        addColorRow(ThemeSlot.SWIPE_SIM2, stepPx)
        addColorRow(ThemeSlot.SWIPE_TEXT, stepPx)
        addColorRow(ThemeSlot.SWIPE_RIN, stepPx)
        addColorRow(ThemeSlot.SWIPE_RIN_TEXT, stepPx)
    }

    // 発着信履歴 on the contact screen. Its look is denwa's own (served by denwa), so only these two here.
    private fun addContactCallHistorySection(primaryColor: Int, stepPx: Int) {
        addSwitchRow(
            title = getString(R.string.contact_call_history_enable),
            icon = RowIcon.CALL_LOG,
            checked = config.contactCallHistory,
            indent = stepPx,
            description = getString(R.string.contact_call_history_desc),
        ) { config.contactCallHistory = it }
        lateinit var countRow: ItemThemeValueBinding
        countRow = addValueRow(getString(R.string.contact_call_history_count), config.contactCallHistoryCount.toString(), stepPx, icon = RowIcon.CALL_LOG) {
            val choices = CONTACT_CALL_HISTORY_COUNTS
            val items = ArrayList(choices.mapIndexed { index, count -> RadioItem(index, count.toString(), count) })
            val checked = choices.indexOf(config.contactCallHistoryCount).coerceAtLeast(0)
            RadioGroupDialog(this, items, checked) { value ->
                config.contactCallHistoryCount = value as Int
                countRow.themeValueValue.text = value.toString()
            }
        }
    }

    private fun rebuildRowStyling(entries: List<RowFieldEntry>, stepPx: Int) {
        val container = rowStylingContainer ?: return
        container.removeAllViews()
        val shown = entries.filter { it.checked }
        if (shown.isEmpty()) {
            return
        }
        addSubgroupHeader(getString(R.string.theme_rows_styling), getProperPrimaryColor(), stepPx, container)
        shown.forEach { addTextSlot(it.field.slot, stepPx * 2, stepPx, container) }
    }

    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addSlider(title: String, value: Int, max: Int, indent: Int, onChange: (Int) -> Unit) {
        val textColor = getProperTextColor()
        val b = ItemThemeSliderBinding.inflate(layoutInflater, body, false)
        b.themeSliderTitle.text = title
        b.themeSliderTitle.setTextColor(textColor)
        b.themeSliderTitle.setRowIcon(RowIcon.SIZE)
        b.themeSliderValue.setTextColor(textColor)
        b.themeSliderValue.text = dpLabel(value)
        b.themeSliderSeekbar.max = max
        b.themeSliderSeekbar.progress = value
        b.themeSliderSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                b.themeSliderValue.text = dpLabel(progress)
                onChange(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        indentRow(b.root, indent)
        body.addView(b.root)
    }

    private fun dpLabel(dp: Int) = "$dp dp"

    // A thumbnail-size slider that live-resizes a preview of the contact placeholder as it moves.
    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addThumbnailSlider(indent: Int) {
        val textColor = getProperTextColor()
        val b = ItemThemeThumbnailBinding.inflate(layoutInflater, body, false)
        b.themeThumbnailTitle.text = getString(R.string.theme_rows_thumbnail_size)
        b.themeThumbnailTitle.setTextColor(textColor)
        b.themeThumbnailTitle.setRowIcon(RowIcon.SIZE)
        b.themeThumbnailValue.setTextColor(textColor)
        b.themeThumbnailSeekbar.max = MAX_CONTACTS_LIST_THUMBNAIL_DP
        b.themeThumbnailSeekbar.min = MIN_CONTACTS_LIST_THUMBNAIL_DP
        b.themeThumbnailSeekbar.progress = config.contactsListThumbnailSize
        b.themeThumbnailValue.text = dpLabel(config.contactsListThumbnailSize)
        updateThumbnailPreview(b.themeThumbnailPreview, config.contactsListThumbnailSize)
        b.themeThumbnailSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                config.contactsListThumbnailSize = progress
                config.contactsListRevision += 1
                b.themeThumbnailValue.text = dpLabel(progress)
                updateThumbnailPreview(b.themeThumbnailPreview, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        indentRow(b.root, indent)
        body.addView(b.root)
    }

    private fun updateThumbnailPreview(preview: ImageView, dp: Int) {
        val px = (dp * resources.displayMetrics.density).toInt()
        preview.updateLayoutParams {
            width = px
            height = px
        }
    }

    // Toggle row: the whole row is the tap target; the switch itself is non-interactive. [description],
    // when given, is the explanation beneath the title. An [explained] item is laid out the black-yellow
    // way — bold accent label, dim-gold explanation, thin dim-gold rule down its left edge; a plain
    // second line (an app's package name in the picker) keeps the label normal and has no rule.
    private fun addSwitchRow(
        title: String,
        checked: Boolean,
        indent: Int,
        description: String? = null,
        explained: Boolean = description != null,
        parent: ViewGroup = body,
        icon: RowIcon = RowIcon.TOGGLE,
        onChange: (Boolean) -> Unit,
    ): ItemThemeSwitchBinding {
        val b = ItemThemeSwitchBinding.inflate(layoutInflater, parent, false)
        b.root.background = rippleDrawable()
        val note = themeColor(ThemeSlot.SETTINGS_NOTE)
        b.themeSwitchLabel.text = title
        b.themeSwitchLabel.setTextColor(if (explained) getProperPrimaryColor() else getProperTextColor())
        if (explained) b.themeSwitchLabel.setTypeface(b.themeSwitchLabel.typeface, Typeface.BOLD)
        b.themeSwitchLabel.setRowIcon(icon)
        b.themeSwitchDesc.setPaddingRelative(rowIconInset(), b.themeSwitchDesc.paddingTop, 0, 0)
        b.themeSwitchDesc.text = description.orEmpty()
        b.themeSwitchDesc.setTextColor(note)
        b.themeSwitchDesc.beVisibleIf(description != null)
        b.themeSwitchRule.setBackgroundColor(note)
        b.themeSwitchRule.beVisibleIf(explained)
        b.themeSwitch.minHeight = 0
        b.themeSwitch.minimumHeight = 0
        b.themeSwitch.isChecked = checked
        b.root.setOnClickListener {
            b.themeSwitch.toggle()
            onChange(b.themeSwitch.isChecked)
        }
        indentRow(b.root, indent)
        parent.addView(b.root)
        return b
    }

    /**
     * The automation-token row: label plus the abbreviated token, tapping anywhere copies the full token,
     * and a Regenerate action on the right warns before invalidating pasted copies.
     */
    private fun addTokenRow(
        indent: Int,
        token: String,
        onCopy: () -> Unit,
        onRegenerate: (ItemThemeTokenBinding) -> Unit,
    ) {
        val b = ItemThemeTokenBinding.inflate(layoutInflater, body, false)
        b.themeTokenLabel.text = getString(R.string.automation_token)
        b.themeTokenLabel.setTextColor(getProperTextColor())
        b.themeTokenLabel.setRowIcon(RowIcon.SECRET)
        b.themeTokenValue.text = abbreviateToken(token)
        b.themeTokenValue.setTextColor(getProperTextColor())
        b.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        b.themeTokenRegenerate.setTextColor(getProperPrimaryColor())
        // An action, so a pill (regular weight) — never bare text.
        b.themeTokenRegenerate.background = pillDrawable(getProperPrimaryColor(), getProperBackgroundColor(), main = false)
        b.root.setOnClickListener { onCopy() }
        b.themeTokenRegenerate.setOnClickListener { onRegenerate(b) }
        indentRow(b.root, indent)
        body.addView(b.root)
    }

    // Shown abbreviated so the secret is not left on screen; the tap still copies it in full.
    private fun abbreviateToken(token: String): String =
        if (token.length <= TOKEN_ABBREVIATION_EDGE * 2) {
            token
        } else {
            token.take(TOKEN_ABBREVIATION_EDGE) + "…" + token.takeLast(TOKEN_ABBREVIATION_EDGE)
        }

    private fun addValueRow(
        title: String,
        value: String,
        indent: Int,
        icon: RowIcon = RowIcon.VALUE,
        onClick: () -> Unit,
    ): ItemThemeValueBinding {
        val textColor = getProperTextColor()
        val b = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        b.themeValueLabel.text = title
        b.themeValueLabel.setTextColor(textColor)
        b.themeValueLabel.setRowIcon(icon)
        b.themeValueValue.text = value
        // The current value in the accent colour: it is what a tap changes.
        b.themeValueValue.setTextColor(getProperPrimaryColor())
        b.root.setOnClickListener { onClick() }
        indentRow(b.root, indent)
        body.addView(b.root)
        return b
    }

    /**
     * A pressable row (black-yellow theme, "every pressable row is a pill"): an outlined pill as wide as
     * its label — 3 dp frame and bold for the [main] action of its section, 1.25 dp and normal otherwise —
     * with a › inside when it [opens] another screen, picker or panel (none when it acts in place after a
     * confirmation), and its [description] beneath in the settings-note dim gold. Only the pill takes taps.
     */
    private fun addActionRow(
        title: String,
        description: CharSequence?,
        indent: Int,
        main: Boolean = false,
        opens: Boolean = true,
        parent: ViewGroup = body,
        icon: RowIcon = RowIcon.SETTINGS,
        onClick: () -> Unit,
    ): ItemThemeActionBinding {
        val b = ItemThemeActionBinding.inflate(layoutInflater, parent, false)
        val accent = getProperPrimaryColor()
        b.themeActionTitle.text = title
        b.themeActionTitle.setTextColor(accent)
        b.themeActionTitle.setTypeface(null, if (main) Typeface.BOLD else Typeface.NORMAL)
        b.themeActionTitle.setRowIcon(icon)
        b.themeActionDesc.setPaddingRelative(
            b.themeActionDesc.paddingStart + rowIconInset(), b.themeActionDesc.paddingTop, 0, 0,
        )
        b.themeActionChevron.setTextColor(accent)
        b.themeActionChevron.beVisibleIf(opens)
        b.themeActionPill.background = pillDrawable(accent, getProperBackgroundColor(), main)
        b.themeActionPill.setOnClickListener { onClick() }
        setActionDescription(b, description)
        // The indent only — the row keeps its own ≈3 dp around the pill rather than the rows' 2 dp.
        b.root.setPaddingRelative(b.root.paddingStart + indent, b.root.paddingTop, b.root.paddingEnd, b.root.paddingBottom)
        parent.addView(b.root)
        return b
    }

    // The explanation under an action pill; the dim-gold rule runs down the left only when there is one.
    private fun setActionDescription(b: ItemThemeActionBinding, description: CharSequence?) {
        val note = themeColor(ThemeSlot.SETTINGS_NOTE)
        b.themeActionDesc.text = description ?: ""
        b.themeActionDesc.setTextColor(note)
        b.themeActionDesc.beVisibleIf(!description.isNullOrEmpty())
        b.themeActionRule.setBackgroundColor(note)
        b.themeActionRule.beVisibleIf(!description.isNullOrEmpty())
    }

    // A sample timestamp for each age bracket, so pattern rows and the edit dialog show a live example.
    private fun sampleTimestampFor(kindPrefix: String): Long = when (kindPrefix) {
        DETAIL_PATTERN_TODAY_PREFIX -> System.currentTimeMillis()
        DETAIL_PATTERN_YEAR_PREFIX -> System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS * SAMPLE_THIS_YEAR_DAYS
        else -> System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS * SAMPLE_OLDER_DAYS
    }

    private fun formatPatternSample(kindPrefix: String, pattern: String): String =
        sampleTimestampFor(kindPrefix).applyDetailPattern(this, pattern)

    private fun addPatternRow(titleRes: Int, kindPrefix: String, indent: Int) {
        val pattern = config.getDetailPattern(kindPrefix, config.detailTimeFormat)
        val value = "$pattern（${formatPatternSample(kindPrefix, pattern)}）"
        addValueRow(getString(titleRes), value, indent, icon = RowIcon.TIME) {
            editDetailPattern(titleRes, kindPrefix)
        }
    }

    private fun editDetailPattern(titleRes: Int, kindPrefix: String) {
        val mode = config.detailTimeFormat
        val view = DialogPatternInputBinding.inflate(layoutInflater)
        view.patternEdittext.setText(config.getDetailPattern(kindPrefix, mode))
        view.patternPreview.text = formatPatternSample(kindPrefix, config.getDetailPattern(kindPrefix, mode))
        view.patternEdittext.doAfterTextChanged {
            view.patternPreview.text = formatPatternSample(kindPrefix, it?.toString().orEmpty())
        }
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                config.setDetailPattern(kindPrefix, mode, view.patternEdittext.text.toString().trim())
                config.contactsListRevision += 1
                buildRows()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { setupDialogStuff(view.root, this, titleRes) }
    }

    private fun detailTimeFormatLabel() = when (config.detailTimeFormat) {
        TIME_FORMAT_SYSTEM -> R.string.time_format_system
        TIME_FORMAT_24H -> R.string.time_format_24
        TIME_FORMAT_12H -> R.string.time_format_12
        else -> R.string.time_format_japanese
    }

    private fun openDetailTimeFormatPicker() {
        val items = arrayListOf(
            RadioItem(TIME_FORMAT_JAPANESE, getString(R.string.time_format_japanese)),
            RadioItem(TIME_FORMAT_SYSTEM, getString(R.string.time_format_system)),
            RadioItem(TIME_FORMAT_24H, getString(R.string.time_format_24)),
            RadioItem(TIME_FORMAT_12H, getString(R.string.time_format_12)),
        )
        RadioGroupDialog(this, items, config.detailTimeFormat) {
            config.detailTimeFormat = it as Int
            config.contactsListRevision += 1
            buildRows()
        }
    }

    private fun editColumnSpacer() {
        val view = DialogColumnSpacerBinding.inflate(layoutInflater)
        view.columnSpacerEdittext.setText(config.contactsListColumnSpacer)
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                config.contactsListColumnSpacer = view.columnSpacerEdittext.text.toString()
                config.contactsListRevision += 1
                buildRows()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { setupDialogStuff(view.root, this, R.string.theme_rows_spacer_text) }
    }

    private fun addSubgroupHeader(
        title: String,
        primaryColor: Int,
        indent: Int,
        parent: ViewGroup = body,
    ) {
        val subgroup = ItemThemeSubgroupBinding.inflate(layoutInflater, parent, false)
        subgroup.themeSubgroupLabel.text = title
        subgroup.themeSubgroupLabel.setTextColor(primaryColor)
        subgroup.themeSubgroupRule.setBackgroundColor(primaryColor)
        indentRow(subgroup.root, indent)
        parent.addView(subgroup.root)
    }

    private fun addColorRow(slot: ThemeSlot, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = circleDrawable(themeColor(slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, indent)
        previews[slot] = row.themeColorPreview
        body.addView(row.root)
    }

    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addTextSlot(slot: ThemeSlot, indent: Int, stepPx: Int, parent: ViewGroup = body) {
        val textColor = getProperTextColor()
        val b = ItemThemeTextBinding.inflate(layoutInflater, parent, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }

        // the element's own line sets its colour; the lines beneath set its font, weight and size
        b.themeTextLabel.setRowIcon(RowIcon.COLOR)
        b.themeTextFontTitle.setRowIcon(RowIcon.TEXT)
        b.themeTextWeightTitle.setRowIcon(RowIcon.WEIGHT)
        b.themeTextSizeTitle.setRowIcon(RowIcon.SIZE)
        b.themeTextColorPreview.background = circleDrawable(themeColor(slot))
        b.themeTextFontValue.text = fontDisplayName(config.getFontFamily(slot.key))
        b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(config.getFontWeight(slot.key)).labelRes)
        b.themeTextSizeSeekbar.max = MAX_FONT_SIZE_SP
        b.themeTextSizeSeekbar.progress = config.getFontSize(slot.key)
        b.themeTextSizeValue.text = sizeLabel(config.getFontSize(slot.key))
        refreshSample(b, slot)

        b.themeTextColorRow.setOnClickListener { openTextColorPicker(slot, b) }
        b.themeTextFontRow.setOnClickListener { openFontPicker(slot, b) }
        b.themeTextWeightRow.setOnClickListener { openWeightPicker(slot, b) }
        b.themeTextSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                config.setFontSize(slot.key, progress)
                b.themeTextSizeValue.text = sizeLabel(progress)
                refreshSample(b, slot)
                bumpRowRevisionIfNeeded(slot)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        indentRow(b.root, indent)
        // the element's font / weight / size / sample sit one full step deeper than its label row
        indentRow(b.themeTextFontRow, stepPx)
        indentRow(b.themeTextWeightRow, stepPx)
        indentRow(b.themeTextSizeRow, stepPx)
        indentRow(b.themeTextSample, stepPx)
        parent.addView(b.root)
    }

    /**
     * Every row on this page (and in the Export / Import panel) passes through here, so this is also
     * where the vertical rhythm is set. The commons row style pads 20dp above and below a single line of
     * text — 40dp per row, which on a page this long is screens of whitespace — so the vertical padding
     * is replaced with [ROW_PADDING_VERTICAL_DP] while the horizontal padding and the indent are kept.
     */
    // The row's icon, before its label and in the colour of what the row does.
    private fun TextView.setRowIcon(icon: RowIcon) {
        val density = resources.displayMetrics.density
        val size = (ROW_ICON_DP * density).toInt()
        val drawable = resources.getColoredDrawableWithColor(icon.res, themeColor(icon.role))
        drawable.setBounds(0, 0, size, size)
        setCompoundDrawablesRelative(drawable, null, null, null)
        compoundDrawablePadding = (ROW_ICON_GAP_DP * density).toInt()
    }

    // How far a line beneath a label is moved in, so that it starts under the label's text and not under its icon.
    private fun rowIconInset() = ((ROW_ICON_DP + ROW_ICON_GAP_DP) * resources.displayMetrics.density).toInt()

    private fun indentRow(view: android.view.View, indent: Int) {
        val vertical = (ROW_PADDING_VERTICAL_DP * resources.displayMetrics.density).toInt()
        // No platform minimum: a row is as tall as its content.
        view.minimumHeight = 0
        view.setPaddingRelative(view.paddingStart + indent, vertical, view.paddingEnd, vertical)
    }

    private fun refreshSample(b: ItemThemeTextBinding, slot: ThemeSlot) {
        b.themeTextSample.showFontSample(
            config.getFontFamily(slot.key),
            config.getFontWeight(slot.key),
            config.getFontSize(slot.key),
            themeColor(slot)
        )
    }

    private fun sizeLabel(sp: Int) = if (sp > 0) "$sp sp" else getString(R.string.theme_size_default)

    // Styling a contacts-list field or a letter-section element must trigger a list redraw; other
    // slots don't affect the list.
    private fun bumpRowRevisionIfNeeded(slot: ThemeSlot) {
        if (slot.group == ThemeGroup.ROWS || slot.group == ThemeGroup.SECTIONS || slot.group == ThemeGroup.SWIPE_CALL) {
            config.contactsListRevision += 1
        }
    }

    private fun openColorPicker(slot: ThemeSlot) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background = circleDrawable(themeColor(slot))
            }
            bumpRowRevisionIfNeeded(slot)
        }
    }

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            b.themeTextColorPreview.background = circleDrawable(themeColor(slot))
            refreshSample(b, slot)
            bumpRowRevisionIfNeeded(slot)
        }
    }

    private fun openFontPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        FontPickerDialog(
            activity = this,
            onAddFont = {
                pendingFontSlot = slot
                pendingFontBinding = b
                fontImportLauncher.launch(arrayOf("*/*"))
            },
            onPick = { fileName ->
                config.setFontFamily(slot.key, fileName)
                b.themeTextFontValue.text = fontDisplayName(fileName)
                refreshSample(b, slot)
                bumpRowRevisionIfNeeded(slot)
            }
        )
    }

    private fun openWeightPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        val items = ArrayList(FontWeightOption.entries.map { RadioItem(it.value, getString(it.labelRes)) })
        RadioGroupDialog(this, items, config.getFontWeight(slot.key)) {
            val weight = it as Int
            config.setFontWeight(slot.key, weight)
            b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(weight).labelRes)
            refreshSample(b, slot)
            bumpRowRevisionIfNeeded(slot)
        }
    }

    private fun onFontImported(uri: Uri?) {
        val slot = pendingFontSlot
        val b = pendingFontBinding
        pendingFontSlot = null
        pendingFontBinding = null
        if (uri == null || slot == null) {
            return
        }

        val fileName = importFont(uri)
        if (fileName == null) {
            toast(R.string.font_invalid)
            return
        }

        config.setFontFamily(slot.key, fileName)
        b?.themeTextFontValue?.text = fontDisplayName(fileName)
        if (b != null) {
            refreshSample(b, slot)
        }
        bumpRowRevisionIfNeeded(slot)
    }
}
