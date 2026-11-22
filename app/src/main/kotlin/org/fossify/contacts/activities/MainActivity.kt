package org.fossify.contacts.activities

import org.fossify.commons.helpers.uiColor
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.extensions.circleDrawable
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
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
import org.fossify.contacts.extensions.getOwnContacts
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
import org.fossify.contacts.helpers.SystemStorage
import org.fossify.contacts.extensions.getInstalledDialerAppPackage
import org.fossify.contacts.helpers.ALL_TABS_MASK
import org.fossify.contacts.helpers.OPEN_TAB_INTENT_EXTRA
import org.fossify.contacts.helpers.ROUTE_RIN
import org.fossify.contacts.helpers.Rindenwa
import org.fossify.contacts.helpers.TAB_RINDENWA
import org.fossify.contacts.helpers.barTabsList
import org.fossify.contacts.helpers.launchRindenwa
import org.fossify.contacts.helpers.pagerTabsList
import org.fossify.contacts.helpers.loadContactEvents
import org.fossify.contacts.helpers.loadContactExtras
import org.fossify.contacts.helpers.migrateSortFieldKeys
import org.fossify.contacts.interfaces.RefreshContactsListener
import java.util.Arrays

// Search-bar chrome dimensions (dp), scaled by display density at use.
private const val SEARCH_BAR_CORNER_RADIUS_DP = 16f
private const val INACTIVE_COLUMN_BUTTON_ALPHA = 0.35f
private const val SEARCH_BAR_STROKE_DP = 2
private const val DIALPAD_RING_DP = 3
private const val DIALPAD_RIPPLE_ALPHA = 0.3f

// The labels of the two hand-over tabs are the apps' own names, the same in every language.
private const val DENWA_TAB_LABEL = "電話"
private const val RINDENWA_TAB_LABEL = "臨電話"

class MainActivity : SimpleActivity(), RefreshContactsListener {
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowGroupsPage = true
    private var storedContactsListRevision = 0

    // The bottom bar: Favorites, Contacts, 電話, 臨電話 — shared with denwa and Rindenwa, the same however we
    // were opened. Decided once per instance: a hand-over tab whose app is not installed is left out.
    private val barTabs: List<Int> by lazy {
        barTabsList.filter {
            when (it) {
                TAB_CALL_HISTORY -> getInstalledDialerAppPackage() != null
                TAB_RINDENWA -> Rindenwa.isInstalled(this)
                else -> true
            }
        }
    }

    // The pages behind it: Favorites and Contacts under their tabs, then Groups, which has no tab — it is
    // opened from ⋮ (or by paging past Contacts) and shows the bar with nothing selected.
    private val pagerTabs: List<Int> by lazy {
        pagerTabsList.filter { it != TAB_GROUPS || config.showGroupsPage }
    }

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
        // The "manage shown tabs" setting is retired with the shared bar; a tab hidden there long ago
        // must not leave its page blank.
        if (config.showTabs != ALL_TABS_MASK) config.showTabs = ALL_TABS_MASK
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    // UI settings → Maintenance → Keep the phone clean: whatever appeared in system storage since the last
    // look moves into private storage, then the list reloads. One run at a time.
    @Volatile
    private var keepingClean = false

    private fun keepPhoneCleanIfWanted() {
        if (!config.autoKeepClean || keepingClean) return
        keepingClean = true
        ensureBackgroundThread {
            try {
                if (!SystemStorage.status(applicationContext).isClean) {
                    val (moved, _) = SystemStorage.moveEverythingToPrivate(applicationContext) { _, _ -> }
                    runOnUiThread {
                        if (moved > 0) toast(getString(R.string.auto_clean_done, moved))
                        refreshContacts(ALL_TABS_MASK)
                    }
                }
            } finally {
                keepingClean = false
            }
        }
    }

    // The swipe bands wear each 臨 account's own colours, which are set in Rindenwa: ask for the list on
    // every return, and rebuild the lists when it changed.
    private fun refreshRinAccounts() {
        if (!config.swipeToCall || !Rindenwa.isInstalled(this)) return
        val usesRin = listOf(config.swipeLeftRoute, config.swipeRightRoute).any { it.startsWith(ROUTE_RIN) }
        if (!usesRin) return
        val appContext = applicationContext
        ensureBackgroundThread {
            val before = Rindenwa.cachedAccounts(appContext)
            val now = Rindenwa.refreshAccounts(appContext)
            if (now != null && now != before) {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
                    findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.forceListRedraw = true
                    refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        keepPhoneCleanIfWanted()
        refreshRinAccounts()
        if (storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        if (storedShowGroupsPage != config.showGroupsPage) {
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

        // Never a filled slab (black-yellow theme): the background shows through, the accent draws the icon
        // and a thick ring, as on the + button beside it (commons draws that one).
        // Icon, frame and ground are the floating button's own roles, the same three the + button reads.
        val fabFrame = uiColor(UiRole.FAB_FRAME)
        val dialpadIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_dialpad_vector, uiColor(UiRole.FAB_ICON))
        binding.mainDialpadButton.apply {
            setImageDrawable(dialpadIcon)
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(uiColor(UiRole.FAB_GROUND))
                setStroke((DIALPAD_RING_DP * resources.displayMetrics.density).toInt(), fabFrame)
            }
            background = RippleDrawable(ColorStateList.valueOf(fabFrame.adjustAlpha(DIALPAD_RIPPLE_ALPHA)), ring, null)
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
            findItem(R.id.more_apps_from_us).isVisible = resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)
            findItem(R.id.open_groups).isVisible = TAB_GROUPS in pagerTabs && currentFragment != findViewById(R.id.groups_fragment)
            val onContactsTab = currentFragment == findViewById(R.id.contacts_fragment)
            columnButtonIds.forEach { findItem(it).isVisible = onContactsTab }

            // Favorites-only entries: multi-select, which long-press gives up while tap-to-dial owns
            // it, and the sweep that gives every favorite a number to call.
            val onFavoritesTab = currentFragment == findViewById(R.id.favorites_fragment)
            findItem(R.id.select_favorites).isVisible = onFavoritesTab
            findItem(R.id.set_favorite_numbers).isVisible = onFavoritesTab
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
                R.id.open_groups -> binding.viewPager.currentItem = pagerTabs.indexOf(TAB_GROUPS)
                R.id.select_favorites -> findViewById<FavoritesFragment>(R.id.favorites_fragment)?.startSelectMode()
                R.id.set_favorite_numbers ->
                    findViewById<FavoritesFragment>(R.id.favorites_fragment)?.setNumbersForCalling()
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
            storedShowGroupsPage = showGroupsPage
            storedFontSize = fontSize
            storedContactsListRevision = contactsListRevision
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        // the shortcut's disc is the accent role (the stock launcher-icon colour is gone with Customize colors)
        val appIconColor = uiColor(UiRole.ACCENT)
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
        // built here: a disc in the accent role under a plus in the background role
        val plus = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_plus_vector, uiColor(UiRole.BACKGROUND))
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
        val drawable = LayerDrawable(arrayOf(circleDrawable(appIconColor), plus)).apply {
            setLayerInset(1, margin, margin, margin, margin)
        }
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

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = when (pagerTabs.getOrNull(binding.viewPager.currentItem)) {
        TAB_FAVORITES -> findViewById(R.id.favorites_fragment)
        TAB_CONTACTS -> findViewById(R.id.contacts_fragment)
        TAB_GROUPS -> findViewById(R.id.groups_fragment)
        else -> null
    }

    // The bar position of the page being shown, or -1 on Groups, which has no tab.
    private fun currentBarPosition() = barTabs.indexOf(pagerTabs.getOrNull(binding.viewPager.currentItem))

    // Brings the bar in line with the page: its tab selected, or none at all on Groups.
    private fun selectBarTabForPage() {
        val position = currentBarPosition()
        if (position >= 0) {
            binding.mainTabsHolder.getTabAt(position)?.select()
        } else {
            binding.mainTabsHolder.selectTab(null)
            binding.mainTabsFolder.clearSelection()
            setupTabColors()
        }
    }

    private fun setupTabColors() {
        val active = currentBarPosition()
        for (index in 0 until binding.mainTabsHolder.tabCount) {
            val view = binding.mainTabsHolder.getTabAt(index)?.customView
            val isActive = index == active
            updateBottomTabItemColors(view, isActive, getBarTabDrawableId(barTabs[index], isActive))
            colorTabItem(view, themeColor(if (isActive) ThemeSlot.TAB_SELECTED else ThemeSlot.TAB_UNSELECTED))
            boldTabLabel(view, isActive)
        }

        // The folder tabs behind the bar carry its background and the line around the selected tab.
        binding.mainTabsHolder.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.mainTabsFolder.setColors(themeColor(ThemeSlot.TAB_SELECTED), themeColor(ThemeSlot.TAB_BACKGROUND))
    }

    private fun boldTabLabel(view: View?, bold: Boolean) {
        view?.findViewById<TextView>(org.fossify.commons.R.id.tab_item_label)
            ?.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun colorTabItem(view: View?, color: Int) {
        view?.findViewById<ImageView>(org.fossify.commons.R.id.tab_item_icon)?.applyColorFilter(color)
        view?.findViewById<TextView>(org.fossify.commons.R.id.tab_item_label)?.setTextColor(color)
    }

    // The same icons as the other two apps' bars: star, person, clock for 電話's call history, a phone for 臨電話.
    private fun getBarTabDrawableId(tabMask: Int, selected: Boolean) = when (tabMask) {
        TAB_FAVORITES -> if (selected) org.fossify.commons.R.drawable.ic_star_vector else org.fossify.commons.R.drawable.ic_star_outline_vector
        TAB_CONTACTS -> if (selected) org.fossify.commons.R.drawable.ic_person_vector else org.fossify.commons.R.drawable.ic_person_outline_vector
        TAB_CALL_HISTORY -> if (selected) org.fossify.commons.R.drawable.ic_clock_filled_vector else org.fossify.commons.R.drawable.ic_clock_vector
        else -> org.fossify.commons.R.drawable.ic_phone_vector
    }

    private fun getBarTabIcon(tabMask: Int): Drawable =
        resources.getColoredDrawableWithColor(getBarTabDrawableId(tabMask, false), getProperTextColor())

    private fun getBarTabLabel(tabMask: Int): String = when (tabMask) {
        TAB_FAVORITES -> resources.getString(org.fossify.commons.R.string.favorites_tab)
        TAB_CONTACTS -> resources.getString(org.fossify.commons.R.string.contacts_tab)
        TAB_CALL_HISTORY -> DENWA_TAB_LABEL
        else -> RINDENWA_TAB_LABEL
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = pagerTabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                selectBarTabForPage()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
                offerFavoriteNumbersIfShowing()
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
                updateBottomTabItemColors(it.customView, false, getBarTabDrawableId(barTabs[it.position], false))
                colorTabItem(it.customView, themeColor(ThemeSlot.TAB_UNSELECTED))
                boldTabLabel(it.customView, false)
            },
            tabSelectedAction = {
                // 電話 and 臨電話 are launchers, not pages: each hands over to its app's call history and the
                // selection bounces back to the page we are staying on, as the other two apps do for ours.
                val tab = barTabs.getOrNull(it.position)
                if (tab == TAB_CALL_HISTORY || tab == TAB_RINDENWA) {
                    if (tab == TAB_CALL_HISTORY) launchDialerApp(TAB_CALL_HISTORY) else launchRindenwa()
                    Handler(Looper.getMainLooper()).post { selectBarTabForPage() }
                    return@onTabSelectionChanged
                }

                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                binding.viewPager.currentItem = pagerTabs.indexOf(tab)
                setupTabColors()
            }
        )

        binding.mainTabsFolder.attach(binding.mainTabsHolder)
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

        if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
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
            binding.viewPager.adapter = ViewPagerAdapter(this, pagerTabs)
            binding.viewPager.currentItem = takeRequestedTab() ?: getDefaultTab()
            binding.viewPager.post { selectBarTabForPage() }
        }

        this.getOwnContacts { contacts ->
            isGettingContacts = false
            if (isDestroyed || isFinishing) {
                return@getOwnContacts
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

        offerFavoriteNumbersIfShowing()
    }

    // The one-time "give your favorites a number to call" offer, made only while that tab is the one
    // being looked at — it is about the tiles in front of 白い熊, so it must not ambush another tab.
    private fun offerFavoriteNumbersIfShowing() {
        val favorites = findViewById<FavoritesFragment>(R.id.favorites_fragment) ?: return
        if (getCurrentFragment() == favorites) {
            favorites.offerNumbersForCallingOnce()
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

    // Tab requested via OPEN_TAB_INTENT_EXTRA (sent by denwa and Rindenwa for the bar's first two tabs)
    // as a page position; consumed on use, null when absent or not a page of ours.
    private fun takeRequestedTab(): Int? {
        val wantedTab = intent.getIntExtra(OPEN_TAB_INTENT_EXTRA, 0)
        if (wantedTab == 0) {
            return null
        }

        intent.removeExtra(OPEN_TAB_INTENT_EXTRA)
        return pagerTabs.indexOf(wantedTab).takeIf { it >= 0 }
    }

    private fun getDefaultTab(): Int = when (val wanted = config.defaultTab) {
        TAB_LAST_USED -> config.lastUsedViewPagerPage.coerceIn(0, pagerTabs.size - 1)
        else -> pagerTabs.indexOf(wanted).coerceAtLeast(0)
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
