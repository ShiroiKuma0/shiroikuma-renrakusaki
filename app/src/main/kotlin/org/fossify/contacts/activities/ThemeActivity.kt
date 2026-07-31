package org.fossify.contacts.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
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
import org.fossify.contacts.adapters.ContactsListFieldsAdapter
import org.fossify.contacts.databinding.ActivityThemeBinding
import org.fossify.contacts.databinding.DialogColumnSpacerBinding
import org.fossify.contacts.databinding.DialogExportImportBinding
import org.fossify.contacts.databinding.DialogPatternInputBinding
import org.fossify.contacts.databinding.ItemThemeColorBinding
import org.fossify.contacts.databinding.ItemThemeFieldsOrderBinding
import org.fossify.contacts.databinding.ItemThemeSectionBinding
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
import org.fossify.contacts.extensions.tryImportContactsFromFile
import org.fossify.contacts.helpers.SettingsExport
import org.fossify.contacts.helpers.VcfExporter
import org.fossify.contacts.extensions.FontWeightOption
import org.fossify.contacts.extensions.ThemeGroup
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.helpers.ContactsListConfig
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_DIVIDER_DP
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_SPACING_DP
import org.fossify.contacts.helpers.DETAIL_PATTERN_OLDER_PREFIX
import org.fossify.contacts.helpers.DETAIL_PATTERN_TODAY_PREFIX
import org.fossify.contacts.helpers.DETAIL_PATTERN_YEAR_PREFIX
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

// Vertical padding for every row, replacing the commons style's 20dp — the page is a long list of
// one-liners, so it is packed tight rather than spaced like a handful of preference rows.
private const val ROW_PADDING_VERTICAL_DP = 4

// A row description renders at this share of the title's size, dimmed to this alpha.
private const val DESCRIPTION_TEXT_SCALE = 0.85f
private const val DESCRIPTION_ALPHA = 0.6f

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

@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    // Holds the per-field styling controls for the "Contacts' list" section; rebuilt when the field set changes.
    private var rowStylingContainer: LinearLayout? = null

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
        setupMaterialScrollListener(binding.themeNestedScrollview, binding.themeAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow)
        applyTopBarColors(binding.themeAppbar)
        buildRows()
    }

    // Render the slot enum as a section > subgroup > controls cascade.
    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()

        val primaryColor = getProperPrimaryColor()
        val stepPx = (INDENT_STEP_DP * resources.displayMetrics.density).toInt()

        addExportImportSection(primaryColor, stepPx)
        addLanguageSection(primaryColor, stepPx)
        ThemeGroup.entries.forEach { addGroup(it, primaryColor, stepPx) }
    }

    // ---- Export / Import (kojiki-style: persisted folder, last-export line, category checklist) ----

    private fun addExportImportSection(primaryColor: Int, stepPx: Int) {
        addSectionHeader(getString(R.string.eim_title), primaryColor)

        // Two rows only, as in the sister apps: where exports go, and the panel that does the work.
        // Everything selectable — categories, their sub-options, the VCF one-shots — lives in the panel.
        addValueRow(getString(R.string.eim_dir), eximDir()?.name ?: getString(R.string.eim_dir_unset), stepPx) {
            eximDirPicker.launch(eximDirUri())
        }
        addValueRow(getString(R.string.eim_open), lastExportSummary(), stepPx) { showExportImportDialog() }

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
        addActionRow(getString(R.string.export_contacts_to_vcf), stepPx, holder) {
            dialog?.dismiss()
            startVcfExport()
        }
        addActionRow(getString(R.string.import_contacts_from_vcf), stepPx, holder) {
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
        ContactsHelper(this).getContacts(true, false, ignoredContactSources) { contacts ->
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

        // Two rows, in the order every sister app uses: the master switch (default OFF), then the token.
        addSwitchRow(
            title = getString(R.string.enable_automation),
            checked = config.automationEnabled,
            indent = stepPx * 2,
            description = getString(R.string.enable_automation_desc),
        ) {
            config.automationEnabled = it
        }

        addTokenRow(
            indent = stepPx * 2,
            token = config.automationToken,
            onCopy = {
                // Not commons' copyToClipboard: that one toasts the value itself, which would put the
                // full secret back on screen right after we deliberately abbreviated it.
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(getString(R.string.automation_token), config.automationToken))
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

        // All-files access: needed so an automation broadcast can write to an arbitrary absolute path
        // (e.g. 白い熊's archive folder) outside Download/Documents. API 30+ only.
        if (isRPlus()) {
            val granted = Environment.isExternalStorageManager()
            val state = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed)
            addValueRow(getString(R.string.all_files_access), state, stepPx * 2) {
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
        addSectionHeader(getString(R.string.theme_app_language), primaryColor)
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

    private fun addGroup(group: ThemeGroup, primaryColor: Int, stepPx: Int) {
        if (group == ThemeGroup.SECTIONS) {
            addSectionsSection(primaryColor, stepPx)
            return
        }
        if (group == ThemeGroup.ROWS) {
            addRowsSection(primaryColor, stepPx)
            return
        }

        addSectionHeader(getString(group.labelRes), primaryColor)

        var sawAny = false
        var lastSubgroup: Int? = null
        ThemeSlot.entries.filter { it.group == group }.forEach { slot ->
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
    }

    private fun addSectionHeader(title: String, primaryColor: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = title
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionRule.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(section.root)
    }

    // The "Contacts' list" section: the field order-box (tick / drag / column buttons) then a styling
    // control (font / weight / size / color) for each currently-shown field.
    private fun addRowsSection(primaryColor: Int, stepPx: Int) {
        addSectionHeader(getString(R.string.theme_group_rows), primaryColor)

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
        addValueRow(getString(R.string.detail_time_format), getString(detailTimeFormatLabel()), stepPx * 2) {
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

        addSubgroupHeader(getString(R.string.theme_rows_order), primaryColor, stepPx)

        val entries = ContactsListConfig.parse(config.contactsListFields).toMutableList()
        val orderList = ItemThemeFieldsOrderBinding.inflate(layoutInflater, binding.themeHolder, false).root
        orderList.layoutManager = LinearLayoutManager(this)
        val adapter = ContactsListFieldsAdapter(this, entries) {
            config.contactsListFields = ContactsListConfig.serialize(entries)
            config.contactsListRevision += 1
            rebuildRowStyling(entries, stepPx)
        }
        orderList.adapter = adapter
        adapter.attachTo(orderList)
        binding.themeHolder.addView(orderList)

        rowStylingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        binding.themeHolder.addView(rowStylingContainer)
        rebuildRowStyling(entries, stepPx)
    }

    // The "Letter sections" top-level section: the grouped-view toggle, the header letter's text styling,
    // and the underline / framing-divider lines (thickness slider + color each).
    private fun addSectionsSection(primaryColor: Int, stepPx: Int) {
        addSectionHeader(getString(R.string.theme_sections_group), primaryColor)
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
        val b = ItemThemeSliderBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeSliderTitle.text = title
        b.themeSliderTitle.setTextColor(textColor)
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
        binding.themeHolder.addView(b.root)
    }

    private fun dpLabel(dp: Int) = "$dp dp"

    // A thumbnail-size slider that live-resizes a preview of the contact placeholder as it moves.
    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addThumbnailSlider(indent: Int) {
        val textColor = getProperTextColor()
        val b = ItemThemeThumbnailBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeThumbnailTitle.text = getString(R.string.theme_rows_thumbnail_size)
        b.themeThumbnailTitle.setTextColor(textColor)
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
        binding.themeHolder.addView(b.root)
    }

    private fun updateThumbnailPreview(preview: ImageView, dp: Int) {
        val px = (dp * resources.displayMetrics.density).toInt()
        preview.updateLayoutParams {
            width = px
            height = px
        }
    }

    // Toggle row: the whole row is the tap target; the switch itself is non-interactive. [description],
    // when given, becomes a dimmer second line under the title.
    private fun addSwitchRow(
        title: String,
        checked: Boolean,
        indent: Int,
        description: String? = null,
        parent: ViewGroup = binding.themeHolder,
        onChange: (Boolean) -> Unit,
    ): ItemThemeSwitchBinding {
        val b = ItemThemeSwitchBinding.inflate(layoutInflater, parent, false)
        b.themeSwitchLabel.text = if (description == null) title else titleWithDescription(title, description)
        b.themeSwitchLabel.setTextColor(getProperTextColor())
        b.themeSwitch.isChecked = checked
        b.root.setOnClickListener {
            b.themeSwitch.toggle()
            onChange(b.themeSwitch.isChecked)
        }
        indentRow(b.root, indent)
        parent.addView(b.root)
        return b
    }

    // A row's explanation, as a smaller dimmed line below its title — the value rows' styling, without
    // needing a second view in every switch layout.
    private fun titleWithDescription(title: String, description: String): CharSequence =
        SpannableStringBuilder(title).apply {
            append("\n")
            val start = length
            append(description)
            val dimmed = ForegroundColorSpan(getProperTextColor().adjustAlpha(DESCRIPTION_ALPHA))
            setSpan(RelativeSizeSpan(DESCRIPTION_TEXT_SCALE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(dimmed, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        val b = ItemThemeTokenBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeTokenLabel.text = getString(R.string.automation_token)
        b.themeTokenLabel.setTextColor(getProperTextColor())
        b.themeTokenValue.text = abbreviateToken(token)
        b.themeTokenValue.setTextColor(getProperTextColor())
        b.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        b.themeTokenRegenerate.setTextColor(getProperPrimaryColor())
        b.root.setOnClickListener { onCopy() }
        b.themeTokenRegenerate.setOnClickListener { onRegenerate(b) }
        indentRow(b.root, indent)
        binding.themeHolder.addView(b.root)
    }

    // Shown abbreviated so the secret is not left on screen; the tap still copies it in full.
    private fun abbreviateToken(token: String): String =
        if (token.length <= TOKEN_ABBREVIATION_EDGE * 2) {
            token
        } else {
            token.take(TOKEN_ABBREVIATION_EDGE) + "…" + token.takeLast(TOKEN_ABBREVIATION_EDGE)
        }

    private fun addValueRow(title: String, value: String, indent: Int, onClick: () -> Unit): ItemThemeValueBinding {
        val textColor = getProperTextColor()
        val b = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeValueLabel.text = title
        b.themeValueLabel.setTextColor(textColor)
        b.themeValueValue.text = value
        b.themeValueValue.setTextColor(textColor)
        b.root.setOnClickListener { onClick() }
        indentRow(b.root, indent)
        binding.themeHolder.addView(b.root)
        return b
    }

    // Label-only tappable row (no value line) — used for one-shot actions like export/import.
    private fun addActionRow(title: String, indent: Int, parent: ViewGroup = binding.themeHolder, onClick: () -> Unit) {
        val b = ItemThemeValueBinding.inflate(layoutInflater, parent, false)
        b.themeValueLabel.text = title
        b.themeValueLabel.setTextColor(getProperTextColor())
        b.themeValueValue.beGone()
        b.root.setOnClickListener { onClick() }
        indentRow(b.root, indent)
        parent.addView(b.root)
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
        addValueRow(getString(titleRes), value, indent) {
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
        parent: ViewGroup = binding.themeHolder,
    ) {
        val subgroup = ItemThemeSubgroupBinding.inflate(layoutInflater, parent, false)
        subgroup.themeSubgroupLabel.text = title
        subgroup.themeSubgroupLabel.setTextColor(primaryColor)
        subgroup.themeSubgroupRule.setBackgroundColor(primaryColor)
        indentRow(subgroup.root, indent)
        parent.addView(subgroup.root)
    }

    private fun addColorRow(slot: ThemeSlot, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorPreview.background.setTint(themeColor(slot))
        row.root.setOnClickListener { openColorPicker(slot) }
        indentRow(row.root, indent)
        previews[slot] = row.themeColorPreview
        binding.themeHolder.addView(row.root)
    }

    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addTextSlot(slot: ThemeSlot, indent: Int, stepPx: Int, parent: ViewGroup = binding.themeHolder) {
        val textColor = getProperTextColor()
        val b = ItemThemeTextBinding.inflate(layoutInflater, parent, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }

        b.themeTextColorPreview.background.setTint(themeColor(slot))
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
    private fun indentRow(view: android.view.View, indent: Int) {
        val vertical = (ROW_PADDING_VERTICAL_DP * resources.displayMetrics.density).toInt()
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
        if (slot.group == ThemeGroup.ROWS || slot.group == ThemeGroup.SECTIONS) {
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
                previews[slot]?.background?.setTint(themeColor(slot))
            }
            bumpRowRevisionIfNeeded(slot)
        }
    }

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            b.themeTextColorPreview.background.setTint(themeColor(slot))
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
