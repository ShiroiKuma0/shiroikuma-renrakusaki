package org.fossify.contacts.activities

import android.os.Bundle
import android.widget.ImageView
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.contacts.databinding.ActivityThemeBinding
import org.fossify.contacts.databinding.ItemThemeColorBinding
import org.fossify.contacts.databinding.ItemThemeSectionBinding
import org.fossify.contacts.databinding.ItemThemeSubgroupBinding
import org.fossify.contacts.extensions.ThemeGroup
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.resetThemeColor
import org.fossify.contacts.extensions.setThemeColor
import org.fossify.contacts.extensions.themeColor

// How much further each subgroup's color rows are indented past the section's controls.
private const val SUBGROUP_INDENT_DP = 24

class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
        setupMaterialScrollListener(binding.themeNestedScrollview, binding.themeAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow)
        buildRows()
    }

    // Render the slot enum as a section > subgroup > controls cascade.
    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()

        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()
        val indentPx = (SUBGROUP_INDENT_DP * resources.displayMetrics.density).toInt()

        ThemeGroup.entries.forEach { addGroup(it, textColor, primaryColor, indentPx) }
    }

    private fun addGroup(group: ThemeGroup, textColor: Int, primaryColor: Int, indentPx: Int) {
        addSectionHeader(getString(group.labelRes), primaryColor)

        var sawAny = false
        var lastSubgroup: Int? = null
        ThemeSlot.entries.filter { it.group == group }.forEach { slot ->
            val subgroup = slot.subgroupLabelRes
            if (!sawAny || subgroup != lastSubgroup) {
                sawAny = true
                lastSubgroup = subgroup
                subgroup?.let { addSubgroupHeader(getString(it), primaryColor) }
            }
            addColorRow(slot, textColor, indent = if (subgroup != null) indentPx else 0)
        }
    }

    private fun addSectionHeader(title: String, primaryColor: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = title
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionRule.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(section.root)
    }

    private fun addSubgroupHeader(title: String, primaryColor: Int) {
        val subgroup = ItemThemeSubgroupBinding.inflate(layoutInflater, binding.themeHolder, false)
        subgroup.themeSubgroupLabel.text = title
        subgroup.themeSubgroupLabel.setTextColor(primaryColor)
        subgroup.themeSubgroupRule.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(subgroup.root)
    }

    private fun addColorRow(slot: ThemeSlot, textColor: Int, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(textColor)
        row.themeColorPreview.background.setTint(themeColor(slot))
        row.root.setOnClickListener { openPicker(slot) }
        if (indent > 0) {
            row.root.setPaddingRelative(
                row.root.paddingStart + indent,
                row.root.paddingTop,
                row.root.paddingEnd,
                row.root.paddingBottom
            )
        }
        previews[slot] = row.themeColorPreview
        binding.themeHolder.addView(row.root)
    }

    private fun openPicker(slot: ThemeSlot) {
        ColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) {
                setThemeColor(slot, color)
            } else {
                resetThemeColor(slot)
            }

            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background?.setTint(themeColor(slot))
            }
        }
    }
}
