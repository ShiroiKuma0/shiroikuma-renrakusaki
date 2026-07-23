package org.fossify.contacts.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.contacts.R
import org.fossify.contacts.databinding.ActivitySettingsBinding
import org.fossify.contacts.dialogs.ManageAutoBackupsDialog
import org.fossify.contacts.dialogs.ManageVisibleFieldsDialog
import org.fossify.contacts.dialogs.ManageVisibleTabsDialog
import org.fossify.contacts.extensions.*
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)
        applyTopBarColors(binding.settingsAppbar)

        setupThemeAndColors()
        setupCustomizeColors()
        setupManageShownContactFields()
        setupShowAllFieldsWhenViewing()
        setupManageShownTabs()
        setupFontSize()
        setupUseEnglish()
        setupLanguage()
        setupShowContactThumbnails()
        setupShowPhoneNumbers()
        setupEnableNumberFormatting()
        setupShowContactsWithNumbers()
        setupStartNameWithSurname()
        setupMergeDuplicateContacts()
        setupShowCallConfirmation()
        setupShowDialpadButton()
        setupShowPrivateContacts()
        setupOnContactClick()
        setupDefaultTab()
        setupEnableAutomaticBackups()
        setupManageAutomaticBackups()
        updateTextColors(binding.settingsHolder)

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsMainScreenLabel,
            binding.settingsListViewLabel,
            binding.settingsBackupsLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupThemeAndColors() {
        binding.settingsThemeAndColorsHolder.setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupManageShownContactFields() {
        binding.settingsManageContactFieldsHolder.setOnClickListener {
            ManageVisibleFieldsDialog(this) {}
        }
    }

    private fun setupShowAllFieldsWhenViewing() {
        binding.settingsShowAllFieldsWhenViewing.isChecked = config.showAllFieldsWhenViewing
        binding.settingsShowAllFieldsWhenViewingHolder.setOnClickListener {
            binding.settingsShowAllFieldsWhenViewing.toggle()
            config.showAllFieldsWhenViewing = binding.settingsShowAllFieldsWhenViewing.isChecked
        }
    }

    private fun setupManageShownTabs() {
        binding.settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_CONTACTS, getString(org.fossify.commons.R.string.contacts_tab)),
                RadioItem(TAB_FAVORITES, getString(org.fossify.commons.R.string.favorites_tab)),
                RadioItem(TAB_GROUPS, getString(org.fossify.commons.R.string.groups_tab)),
                RadioItem(TAB_LAST_USED, getString(org.fossify.commons.R.string.last_used_tab))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_CONTACTS -> org.fossify.commons.R.string.contacts_tab
            TAB_FAVORITES -> org.fossify.commons.R.string.favorites_tab
            TAB_GROUPS -> org.fossify.commons.R.string.groups_tab
            else -> org.fossify.commons.R.string.last_used_tab
        }
    )

    private fun setupFontSize() {
        binding.settingsFontSize.text = getFontSizeText()
        binding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(org.fossify.commons.R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                binding.settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        binding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupShowContactThumbnails() {
        binding.settingsShowContactThumbnails.isChecked = config.showContactThumbnails
        binding.settingsShowContactThumbnailsHolder.setOnClickListener {
            binding.settingsShowContactThumbnails.toggle()
            config.showContactThumbnails = binding.settingsShowContactThumbnails.isChecked
        }
    }

    private fun setupEnableNumberFormatting(){
        binding.settingsFormatPhoneNumbers.isChecked = config.formatPhoneNumbers
        binding.settingsFormatPhoneNumbersHolder.setOnClickListener{
            binding.settingsFormatPhoneNumbers.toggle()
            config.formatPhoneNumbers = binding.settingsFormatPhoneNumbers.isChecked
        }
    }

    private fun setupShowPhoneNumbers() {
        binding.settingsShowPhoneNumbers.isChecked = config.showPhoneNumbers
        binding.settingsShowPhoneNumbersHolder.setOnClickListener {
            binding.settingsShowPhoneNumbers.toggle()
            config.showPhoneNumbers = binding.settingsShowPhoneNumbers.isChecked
        }
    }

    private fun setupShowContactsWithNumbers() {
        binding.settingsShowOnlyContactsWithNumbers.isChecked = config.showOnlyContactsWithNumbers
        binding.settingsShowOnlyContactsWithNumbersHolder.setOnClickListener {
            binding.settingsShowOnlyContactsWithNumbers.toggle()
            config.showOnlyContactsWithNumbers = binding.settingsShowOnlyContactsWithNumbers.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        binding.settingsStartNameWithSurname.isChecked = config.startNameWithSurname
        binding.settingsStartNameWithSurnameHolder.setOnClickListener {
            binding.settingsStartNameWithSurname.toggle()
            config.startNameWithSurname = binding.settingsStartNameWithSurname.isChecked
        }
    }

    private fun setupShowDialpadButton() {
        binding.settingsShowDialpadButton.isChecked = config.showDialpadButton
        binding.settingsShowDialpadButtonHolder.setOnClickListener {
            binding.settingsShowDialpadButton.toggle()
            config.showDialpadButton = binding.settingsShowDialpadButton.isChecked
        }
    }

    private fun setupShowPrivateContacts() {
        binding.settingsShowPrivateContacts.isChecked = config.showPrivateContacts
        binding.settingsShowPrivateContactsHolder.setOnClickListener {
            binding.settingsShowPrivateContacts.toggle()
            config.showPrivateContacts = binding.settingsShowPrivateContacts.isChecked
        }
    }

    private fun setupOnContactClick() {
        binding.settingsOnContactClick.text = getOnContactClickText()
        binding.settingsOnContactClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ON_CLICK_CALL_CONTACT, getString(org.fossify.commons.R.string.call_contact)),
                RadioItem(ON_CLICK_VIEW_CONTACT, getString(org.fossify.commons.R.string.view_contact)),
                RadioItem(ON_CLICK_EDIT_CONTACT, getString(org.fossify.commons.R.string.edit_contact))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.onContactClick) {
                config.onContactClick = it as Int
                binding.settingsOnContactClick.text = getOnContactClickText()
            }
        }
    }

    private fun getOnContactClickText() = getString(
        when (config.onContactClick) {
            ON_CLICK_CALL_CONTACT -> org.fossify.commons.R.string.call_contact
            ON_CLICK_VIEW_CONTACT -> org.fossify.commons.R.string.view_contact
            else -> org.fossify.commons.R.string.edit_contact
        }
    )

    private fun setupShowCallConfirmation() {
        binding.settingsShowCallConfirmation.isChecked = config.showCallConfirmation
        binding.settingsShowCallConfirmationHolder.setOnClickListener {
            binding.settingsShowCallConfirmation.toggle()
            config.showCallConfirmation = binding.settingsShowCallConfirmation.isChecked
        }
    }

    private fun setupMergeDuplicateContacts() {
        binding.settingsMergeDuplicateContacts.isChecked = config.mergeDuplicateContacts
        binding.settingsMergeDuplicateContactsHolder.setOnClickListener {
            binding.settingsMergeDuplicateContacts.toggle()
            config.mergeDuplicateContacts = binding.settingsMergeDuplicateContacts.isChecked
        }
    }

    private fun setupEnableAutomaticBackups() {
        binding.settingsBackupsLabel.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackupsHolder.beVisibleIf(isRPlus())
        binding.settingsEnableAutomaticBackups.isChecked = config.autoBackup
        binding.settingsEnableAutomaticBackupsHolder.setOnClickListener {
            val wasBackupDisabled = !config.autoBackup
            if (wasBackupDisabled) {
                ManageAutoBackupsDialog(
                    activity = this,
                    onSuccess = {
                        enableOrDisableAutomaticBackups(true)
                        scheduleNextAutomaticBackup()
                    }
                )
            } else {
                cancelScheduledAutomaticBackup()
                enableOrDisableAutomaticBackups(false)
            }
        }
    }

    private fun setupManageAutomaticBackups() {
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(isRPlus() && config.autoBackup)
        binding.settingsManageAutomaticBackupsHolder.setOnClickListener {
            ManageAutoBackupsDialog(
                activity = this,
                onSuccess = {
                    scheduleNextAutomaticBackup()
                }
            )
        }
    }

    private fun enableOrDisableAutomaticBackups(enable: Boolean) {
        config.autoBackup = enable
        binding.settingsEnableAutomaticBackups.isChecked = enable
        binding.settingsManageAutomaticBackupsHolder.beVisibleIf(enable)
    }

}
