package org.fossify.contacts.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.MaterialToolbar
import me.grantland.widget.AutofitHelper
import org.fossify.commons.databases.ContactsDatabase
import org.fossify.commons.databinding.BottomTablayoutItemBinding
import org.fossify.commons.dialogs.ChangeViewTypeDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.adapters.ViewPagerAdapter
import org.fossify.contacts.databinding.ActivityMainBinding
import org.fossify.contacts.dialogs.ChangeSortingDialog
import org.fossify.contacts.dialogs.FilterContactSourcesDialog
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.applyThemeFont
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.handleGenericContactClick
import org.fossify.contacts.extensions.launchDialerApp
import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.extensions.tryImportContactsFromFile
import org.fossify.contacts.fragments.ContactsFragment
import org.fossify.contacts.fragments.FavoritesFragment
import org.fossify.contacts.fragments.MyViewPagerFragment
import org.fossify.contacts.helpers.ALL_TABS_MASK
import org.fossify.contacts.helpers.DIALER_TABS_INTENT_EXTRA
import org.fossify.contacts.helpers.OPEN_TAB_INTENT_EXTRA
import org.fossify.contacts.helpers.dialerTabsList
import org.fossify.contacts.helpers.loadContactEvents
import org.fossify.contacts.helpers.loadContactExtras
import org.fossify.contacts.helpers.migrateSortFieldKeys
import org.fossify.contacts.helpers.tabsList
import org.fossify.contacts.interfaces.RefreshContactsListener
import java.util.Arrays

// Search-bar chrome dimensions (dp), scaled by display density at use.
private const val SEARCH_BAR_CORNER_RADIUS_DP = 16f
private const val INACTIVE_COLUMN_BUTTON_ALPHA = 0.35f
private const val SEARCH_BAR_STROKE_DP = 2

// Survives a recreate (process death, or the deliberate rebuild in onNewIntent) so a hand-off
// session does not drop back to our own bottom bar underneath 白い熊.
private const val DIALER_TABS_STATE_KEY = "dialer_tabs_mask"

class MainActivity : SimpleActivity(), RefreshContactsListener {
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowTabs = 0
    private var storedContactsListRevision = 0

    // Non-zero while this activity instance was entered from denwa's bottom bar: denwa's own visible-tab
    // mask (commons TAB_* bits). The bar then wears denwa's tab set instead of ours — Groups drops out,
    // Recents comes in — so getting back to the dialer never means backing out of this app.
    private var dialerTabsMask = 0

    // The TAB_* masks the bottom bar shows, in bar order: denwa's list during a hand-off, ours otherwise.
    private val barTabs: List<Int>
        get() = if (dialerTabsMask != 0) {
            dialerTabsList.filter { dialerTabsMask and it != 0 }
        } else {
            tabsList.filter { config.showTabs and it != 0 }
        }

    // The bar tabs that are actually pages of ours — the bar minus Recents, which only launches denwa.
    private val pagerTabsMask: Int
        get() = (if (dialerTabsMask != 0) dialerTabsMask else config.showTabs) and ALL_TABS_MASK

    // The "contacts per row" toolbar buttons (一 二 三 四 → 1–4 columns, 詳 → detail rows with
    // last-call / last-SMS lines), left of the sort/filter/overflow icons.
    private val columnButtonIds = listOf(R.id.col_1, R.id.col_2, R.id.col_3, R.id.col_4, R.id.col_detail)
    private val columnButtonLabels = listOf("一", "二", "三", "四", "詳")

    override var isSearchBarEnabled = true

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        // Decided before anything reads the tab set: a launch from denwa's bottom bar wears denwa's tabs
        // for the life of this instance.
        dialerTabsMask = sanitizeDialerTabs(takeDialerTabs() ?: savedInstanceState?.getInt(DIALER_TABS_STATE_KEY) ?: 0)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.mainTabsHolder),
        )
        storeStateVariables()
        setupTabs()
        checkContactPermissions()
        checkWhatsNewDialog()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(DIALER_TABS_STATE_KEY, dialerTabsMask)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // The bar's shape is fixed at setup, so a launch that changes it — the dialer's tab set arriving,
        // or a plain launch dropping it — rebuilds the activity on the new shape. The field is set first
        // so the state this recreate saves already carries the new mode, and the intent keeps its extras,
        // so the fresh onCreate() consumes them exactly as a cold start does.
        val wantedDialerTabs = sanitizeDialerTabs(intent.getIntExtra(DIALER_TABS_INTENT_EXTRA, 0))
        if (wantedDialerTabs != dialerTabsMask) {
            dialerTabsMask = wantedDialerTabs
            config.lastUsedViewPagerPage = 0
            recreate()
            return
        }

        intent.removeExtra(DIALER_TABS_INTENT_EXTRA)
        takeRequestedTab()?.let {
            binding.viewPager.currentItem = it
        }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            werePermissionsHandled = true
            if (it) {
                handlePermission(PERMISSION_WRITE_CONTACTS) {
                    handlePermission(PERMISSION_GET_ACCOUNTS) {
                        initFragments()
                    }
                }
            } else {
                initFragments()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        // In a hand-off session the bar comes from denwa, not from config.showTabs — restarting on a
        // change to ours would only throw the session away (the intent extras are already consumed).
        if (dialerTabsMask == 0 && storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        val configShowContactThumbnails = config.showContactThumbnails
        if (storedShowContactThumbnails != configShowContactThumbnails) {
            getAllFragments().forEach {
                it?.showContactThumbnailsChanged(configShowContactThumbnails)
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.mainTabsHolder.background = ColorDrawable(getProperBackgroundColor())
        binding.mainTabsHolder.setSelectedTabIndicatorColor(properPrimaryColor)
        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), properPrimaryColor)
        }

        updateMenuColors()
        setupTabColors()

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
            findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.startNameWithSurnameChanged(configStartNameWithSurname)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        // A change to the configurable contacts-list fields/styling forces the list rows to be rebuilt.
        if (storedContactsListRevision != config.contactsListRevision) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
            findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.forceListRedraw = true
        }

        if (werePermissionsHandled && !isFirstResume) {
            if (binding.viewPager.adapter == null) {
                initFragments()
            } else {
                refreshContacts(ALL_TABS_MASK)
            }
        }

        val dialpadIcon =
            resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(properPrimaryColor)
            beVisibleIf(config.showDialpadButton)
        }

        isFirstResume = false
        checkShortcuts()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            ContactsDatabase.destroyInstance()
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            false
        }
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.sort).isVisible = currentFragment != findViewById(R.id.groups_fragment)
            findItem(R.id.filter).isVisible = currentFragment != findViewById(R.id.groups_fragment)
            findItem(R.id.dialpad).isVisible = !config.showDialpadButton
            findItem(R.id.change_view_type).isVisible = currentFragment == findViewById(R.id.favorites_fragment)
            findItem(R.id.column_count).isVisible = currentFragment == findViewById(R.id.favorites_fragment) && config.viewType == VIEW_TYPE_GRID
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
            val onContactsTab = currentFragment == findViewById(R.id.contacts_fragment)
            columnButtonIds.forEach { findItem(it).isVisible = onContactsTab }
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu)
        binding.mainMenu.toggleHideOnScroll(false)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchOpenListener = {
            binding.mainMenu.post { styleSearchBar() }
        }

        binding.mainMenu.onSearchClosedListener = {
            getAllFragments().forEach {
                it?.onSearchClosed()
            }
            binding.mainMenu.post { styleSearchBar() }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            getCurrentFragment()?.onSearchQueryChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.ui_settings -> launchUiSettings()
                R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                R.id.filter -> showFilterDialog()
                R.id.dialpad -> launchDialpad()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.change_view_type -> changeViewType()
                R.id.column_count -> changeColumnCount()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        setupOverflowLongPress()
        binding.mainMenu.requireToolbar().post { applyColumnButtons() }
    }

    // The 一 二 三 四 / 詳 buttons: set their text, styling (font/color via COLUMN_BUTTONS) and active
    // state, and wire each to switch the main Contacts list — 1–4 contacts per row, or the detail rows.
    private fun applyColumnButtons() {
        val toolbar = binding.mainMenu.requireToolbar()
        val current = config.contactsListColumns
        val detail = config.contactsListDetailMode
        columnButtonIds.forEachIndexed { index, id ->
            val isDetailButton = id == R.id.col_detail
            (toolbar.menu.findItem(id)?.actionView as? TextView)?.apply {
                text = columnButtonLabels[index]
                setTextColor(themeColor(ThemeSlot.COLUMN_BUTTONS))
                applyThemeFont(ThemeSlot.COLUMN_BUTTONS)
                val isActive = if (isDetailButton) detail else !detail && index + 1 == current
                alpha = if (isActive) 1f else INACTIVE_COLUMN_BUTTON_ALPHA
                setOnClickListener {
                    if (isDetailButton) enableDetailMode() else setContactsColumns(index + 1)
                }
            }
        }
    }

    private fun setContactsColumns(columns: Int) {
        if (config.contactsListColumns != columns || config.contactsListDetailMode) {
            config.contactsListColumns = columns
            config.contactsListDetailMode = false
            findViewById<ContactsFragment>(R.id.contacts_fragment)?.columnCountChanged()
        }
        applyColumnButtons()
    }

    // 詳: ask for the call-log + SMS permissions first (a denied line just stays hidden), then flip
    // the mode and refresh so the newly readable data gets loaded.
    private fun enableDetailMode() {
        if (config.contactsListDetailMode) {
            return
        }
        handlePermission(PERMISSION_READ_CALL_LOG) {
            handlePermission(PERMISSION_READ_SMS) {
                config.contactsListDetailMode = true
                applyColumnButtons()
                findViewById<ContactsFragment>(R.id.contacts_fragment)?.columnCountChanged()
                refreshContacts(TAB_CONTACTS)
            }
        }
    }

    // Long-pressing the overflow ("⋮") icon jumps straight to the 白い熊 連絡先 UI page. If the overflow
    // button can't be located the menu item still works, so this is a best-effort shortcut.
    private fun setupOverflowLongPress() {
        val toolbar = binding.mainMenu.requireToolbar()
        toolbar.post {
            val description = getString(androidx.appcompat.R.string.abc_action_menu_overflow_description)
            findViewByContentDescription(toolbar, description)?.setOnLongClickListener {
                launchUiSettings()
                true
            }
        }
    }

    private fun findViewByContentDescription(view: View, description: CharSequence): View? {
        if (description == view.contentDescription) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewByContentDescription(view.getChildAt(i), description)?.let { return it }
            }
        }
        return null
    }

    private fun launchUiSettings() {
        hideKeyboard()
        startActivity(Intent(this, ThemeActivity::class.java))
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            findViewById<FavoritesFragment>(R.id.favorites_fragment)?.updateFavouritesAdapter()
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(org.fossify.commons.R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                findViewById<FavoritesFragment>(R.id.favorites_fragment)?.columnCountChanged()
            }
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
        styleSearchBar()
    }

    // Apply the granular search-bar theme on top of the commons defaults (must run after updateColors).
    private fun styleSearchBar() {
        val menu = binding.mainMenu
        val radiusPx = SEARCH_BAR_CORNER_RADIUS_DP * resources.displayMetrics.density
        val strokePx = (SEARCH_BAR_STROKE_DP * resources.displayMetrics.density).toInt()

        menu.findViewById<View>(org.fossify.commons.R.id.toolbar_container)?.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(themeColor(ThemeSlot.SEARCH_FILL))
                setStroke(strokePx, themeColor(ThemeSlot.SEARCH_BORDER))
            }

        menu.findViewById<EditText>(org.fossify.commons.R.id.top_toolbar_search)?.apply {
            setTextColor(themeColor(ThemeSlot.SEARCH_TEXT))
            setHintTextColor(themeColor(ThemeSlot.SEARCH_HINT))
            applyThemeFont(ThemeSlot.SEARCH_TEXT)
        }

        menu.findViewById<ImageView>(org.fossify.commons.R.id.top_toolbar_search_icon)
            ?.applyColorFilter(themeColor(ThemeSlot.SEARCH_ICON))

        // the toolbar's action icons (sort/filter/…), the overflow "⋮" and the overflow popup's text
        menu.findViewById<MaterialToolbar>(org.fossify.commons.R.id.top_toolbar)?.let {
            styleToolbarMenu(it, themeColor(ThemeSlot.SEARCH_ACTION_ICON), themeColor(ThemeSlot.SEARCH_MENU_TEXT))
        }

        applyColumnButtons()
    }

    private fun styleToolbarMenu(toolbar: MaterialToolbar, iconColor: Int, textColor: Int) {
        toolbar.overflowIcon?.applyColorFilter(iconColor)
        val toolbarMenu = toolbar.menu
        for (i in 0 until toolbarMenu.size()) {
            val item = toolbarMenu.getItem(i)
            item.icon?.applyColorFilter(iconColor)
            // colour the overflow popup's item text (bar items show as icons, so this only shows in the popup);
            // start from the plain text so re-styling on each resume doesn't stack spans
            item.title = item.title?.toString()?.let { coloredText(it, textColor) }
        }
    }

    private fun coloredText(text: String, color: Int) = SpannableString(text).apply {
        setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowContactThumbnails = showContactThumbnails
            storedShowPhoneNumbers = showPhoneNumbers
            storedStartNameWithSurname = startNameWithSurname
            storedShowTabs = showTabs
            storedFontSize = fontSize
            storedContactsListRevision = contactsListRevision
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val createNewContact = getCreateNewContactShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = Arrays.asList(createNewContact)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(org.fossify.commons.R.string.create_new_contact)
        val drawable = resources.getDrawable(org.fossify.commons.R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(org.fossify.commons.R.id.shortcut_plus_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, EditContactActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "create_new_contact")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? {
        val showTabs = pagerTabsMask
        val fragments = arrayListOf<MyViewPagerFragment<*>>()
        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(findViewById(R.id.contacts_fragment))
        }

        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(findViewById(R.id.favorites_fragment))
        }

        if (showTabs and TAB_GROUPS != 0) {
            fragments.add(findViewById(R.id.groups_fragment))
        }

        return fragments.getOrNull(binding.viewPager.currentItem)
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])
        colorTabItem(activeView, themeColor(ThemeSlot.TAB_SELECTED))

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
            colorTabItem(inactiveView, themeColor(ThemeSlot.TAB_UNSELECTED))
        }

        binding.mainTabsHolder.setBackgroundColor(themeColor(ThemeSlot.TAB_BACKGROUND))
    }

    private fun colorTabItem(view: View?, color: Int) {
        view?.findViewById<ImageView>(org.fossify.commons.R.id.tab_item_icon)?.applyColorFilter(color)
        view?.findViewById<TextView>(org.fossify.commons.R.id.tab_item_label)?.setTextColor(color)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    // Indexed by bar position, not by page: in a hand-off the bar carries one more entry (Recents) than
    // the pager has pages, and setupTabColors() walks every bar position.
    private fun getSelectedTabDrawableIds() = barTabs.map {
        when (it) {
            TAB_CONTACTS -> org.fossify.commons.R.drawable.ic_person_vector
            TAB_FAVORITES -> org.fossify.commons.R.drawable.ic_star_vector
            TAB_CALL_HISTORY -> org.fossify.commons.R.drawable.ic_clock_filled_vector
            else -> org.fossify.commons.R.drawable.ic_people_vector
        }
    }

    private fun getDeselectedTabDrawableIds() = barTabs.map {
        when (it) {
            TAB_CONTACTS -> org.fossify.commons.R.drawable.ic_person_outline_vector
            TAB_FAVORITES -> org.fossify.commons.R.drawable.ic_star_outline_vector
            TAB_CALL_HISTORY -> org.fossify.commons.R.drawable.ic_clock_vector
            else -> org.fossify.commons.R.drawable.ic_people_outline_vector
        }
    }

    // Bar icon and label by TAB_* mask. Recents deliberately borrows denwa's own clock icon and
    // "call history" label, so no new resources are needed and the two bars are pixel-identical.
    private fun getBarTabIcon(tabMask: Int): Drawable {
        val drawableId = when (tabMask) {
            TAB_CONTACTS -> org.fossify.commons.R.drawable.ic_person_vector
            TAB_FAVORITES -> org.fossify.commons.R.drawable.ic_star_vector
            TAB_CALL_HISTORY -> org.fossify.commons.R.drawable.ic_clock_vector
            else -> org.fossify.commons.R.drawable.ic_people_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getBarTabLabel(tabMask: Int) = resources.getString(
        when (tabMask) {
            TAB_CONTACTS -> org.fossify.commons.R.string.contacts_tab
            TAB_FAVORITES -> org.fossify.commons.R.string.favorites_tab
            TAB_CALL_HISTORY -> org.fossify.commons.R.string.call_history_tab
            else -> org.fossify.commons.R.string.groups_tab
        }
    )

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        binding.viewPager.onGlobalLayout {
            refreshContacts(ALL_TABS_MASK)
            refreshMenuItems()
        }

        handleExternalIntent()
        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }
    }

    private fun handleExternalIntent() {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }

        if (uri != null) {
            tryImportContactsFromFile(uri) { success ->
                if (success) {
                    runOnUiThread {
                        refreshContacts(ALL_TABS_MASK)
                    }
                }
            }
            intent.action = null
        }
    }

    private fun setupTabs() {
        binding.mainTabsHolder.removeAllTabs()
        barTabs.forEach { tabMask ->
            binding.mainTabsHolder.newTab().setCustomView(org.fossify.commons.R.layout.bottom_tablayout_item).apply tab@{
                customView?.let {
                    BottomTablayoutItemBinding.bind(it)
                }?.apply {
                    tabItemIcon.setImageDrawable(getBarTabIcon(tabMask))
                    tabItemLabel.text = getBarTabLabel(tabMask)
                    AutofitHelper.create(tabItemLabel)
                    binding.mainTabsHolder.addTab(this@tab)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                // Recents is a launcher, not a page: it hands back to denwa and bounces the selection to
                // the page we are actually staying on, the way denwa does for its own hand-off tabs.
                if (barTabs.getOrNull(it.position) == TAB_CALL_HISTORY) {
                    launchDialerApp(TAB_CALL_HISTORY)
                    Handler(Looper.getMainLooper()).post {
                        binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.select()
                    }
                    return@onTabSelectionChanged
                }

                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    private fun launchDialpad() {
        hideKeyboard()
        Intent(Intent.ACTION_DIAL).apply {
            try {
                startActivity(this)
            } catch (e: ActivityNotFoundException) {
                toast(org.fossify.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_JODA or LICENSE_GLIDE or LICENSE_GSON or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(org.fossify.commons.R.string.faq_9_title_commons, org.fossify.commons.R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_2_title_commons, org.fossify.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_6_title_commons, org.fossify.commons.R.string.faq_6_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_7_title_commons, org.fossify.commons.R.string.faq_7_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing || isGettingContacts) {
            return
        }

        isGettingContacts = true

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = ViewPagerAdapter(this, tabsList, pagerTabsMask)
            binding.viewPager.currentItem = takeRequestedTab() ?: getDefaultTab()
        }

        ContactsHelper(this).getContacts { contacts ->
            isGettingContacts = false
            if (isDestroyed || isFinishing) {
                return@getContacts
            }

            // Refresh readings (フリガナ) + lookup keys first — the grouped list buckets and sorts by
            // them — and the last-call/last-SMS data the 詳 detail rows show. The rekey pass rides
            // along: it needs both the lookup keys just loaded and the contacts they belong to.
            ensureBackgroundThread {
                loadContactExtras()
                migrateSortFieldKeys(contacts)
                loadContactEvents()
                runOnUiThread {
                    if (isDestroyed || isFinishing) {
                        return@runOnUiThread
                    }
                    dispatchRefreshedContacts(refreshTabsMask, contacts)
                }
            }
        }
    }

    private fun dispatchRefreshedContacts(refreshTabsMask: Int, contacts: ArrayList<Contact>) {
        if (refreshTabsMask and TAB_CONTACTS != 0) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.apply {
                skipHashComparing = true
                refreshContacts(contacts)
            }
        }

        if (refreshTabsMask and TAB_FAVORITES != 0) {
            findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.apply {
                skipHashComparing = true
                refreshContacts(contacts)
            }
        }

        if (refreshTabsMask and TAB_GROUPS != 0) {
            findViewById<MyViewPagerFragment<*>>(R.id.groups_fragment)?.apply {
                if (refreshTabsMask == TAB_GROUPS) {
                    skipHashComparing = true
                }
                refreshContacts(contacts)
            }
        }

        if (binding.mainMenu.isSearchOpen) {
            getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
        }
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    private fun getAllFragments() = arrayListOf<MyViewPagerFragment<*>?>(
        findViewById(R.id.contacts_fragment),
        findViewById(R.id.favorites_fragment),
        findViewById(R.id.groups_fragment)
    )

    // Tab requested via OPEN_TAB_INTENT_EXTRA (sent by our Phone fork) as a page position;
    // consumed on use, null when absent or when the requested tab is hidden.
    private fun takeRequestedTab(): Int? {
        val wantedTab = intent.getIntExtra(OPEN_TAB_INTENT_EXTRA, 0)
        if (wantedTab == 0) {
            return null
        }

        intent.removeExtra(OPEN_TAB_INTENT_EXTRA)
        val showTabs = pagerTabsMask
        if (showTabs and wantedTab == 0) {
            return null
        }

        return when (wantedTab) {
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabs and TAB_CONTACTS != 0) 1 else 0
            else -> null
        }
    }

    // Denwa's own visible-tab mask, sent with DIALER_TABS_INTENT_EXTRA when this launch came from the
    // dialer's bottom bar. Consumed on use like takeRequestedTab(); null when absent.
    private fun takeDialerTabs(): Int? {
        val mask = intent.getIntExtra(DIALER_TABS_INTENT_EXTRA, 0)
        if (mask == 0) {
            return null
        }

        intent.removeExtra(DIALER_TABS_INTENT_EXTRA)
        return mask
    }

    // A dialer mask holding none of our own pages would leave the pager empty, so it is no hand-off at all.
    private fun sanitizeDialerTabs(mask: Int) = if (mask and ALL_TABS_MASK != 0) mask else 0

    private fun getDefaultTab(): Int {
        val showTabsMask = pagerTabsMask
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_GROUPS > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
