package org.fossify.contacts.activities

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.contacts.R
import org.fossify.contacts.adapters.ContactsListFieldsAdapter
import org.fossify.contacts.databinding.ActivityThemeBinding
import org.fossify.contacts.databinding.DialogColumnSpacerBinding
import org.fossify.contacts.databinding.ItemThemeColorBinding
import org.fossify.contacts.databinding.ItemThemeFieldsOrderBinding
import org.fossify.contacts.databinding.ItemThemeSectionBinding
import org.fossify.contacts.databinding.ItemThemeSliderBinding
import org.fossify.contacts.databinding.ItemThemeSubgroupBinding
import org.fossify.contacts.databinding.ItemThemeTextBinding
import org.fossify.contacts.databinding.ItemThemeValueBinding
import org.fossify.contacts.dialogs.AlphaColorPickerDialog
import org.fossify.contacts.dialogs.FontPickerDialog
import org.fossify.contacts.extensions.FontWeightOption
import org.fossify.contacts.extensions.ThemeGroup
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.helpers.ContactsListConfig
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_DIVIDER_DP
import org.fossify.contacts.helpers.MAX_CONTACTS_LIST_SPACING_DP
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

// Each cascade level is indented one more step: section contents one step in, subgroup contents two.
private const val INDENT_STEP_DP = 72

@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    // Holds the per-field styling controls for the "Contacts' list" section; rebuilt when the field set changes.
    private var rowStylingContainer: LinearLayout? = null

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

        ThemeGroup.entries.forEach { addGroup(it, primaryColor, stepPx) }
    }

    private fun addGroup(group: ThemeGroup, primaryColor: Int, stepPx: Int) {
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

        addSubgroupHeader(getString(R.string.theme_rows_spacer_group), primaryColor, stepPx)
        addValueRow(getString(R.string.theme_rows_spacer_text), config.contactsListColumnSpacer, stepPx * 2) {
            editColumnSpacer()
        }
        addTextSlot(ThemeSlot.COLUMN_SPACER, stepPx * 2, stepPx)

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

    private fun addValueRow(title: String, value: String, indent: Int, onClick: () -> Unit) {
        val textColor = getProperTextColor()
        val b = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeValueLabel.text = title
        b.themeValueLabel.setTextColor(textColor)
        b.themeValueValue.text = value
        b.themeValueValue.setTextColor(textColor)
        b.root.setOnClickListener { onClick() }
        indentRow(b.root, indent)
        binding.themeHolder.addView(b.root)
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

    private fun indentRow(view: android.view.View, indent: Int) {
        if (indent > 0) {
            view.setPaddingRelative(view.paddingStart + indent, view.paddingTop, view.paddingEnd, view.paddingBottom)
        }
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

    // Styling a contacts-list field must trigger a list redraw; other slots don't affect the list.
    private fun bumpRowRevisionIfNeeded(slot: ThemeSlot) {
        if (slot.group == ThemeGroup.ROWS) {
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
