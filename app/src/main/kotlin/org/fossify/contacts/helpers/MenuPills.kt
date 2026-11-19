package org.fossify.contacts.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.contacts.R
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.themeColor
import org.fossify.commons.R as CR

/**
 * What a menu row needs to know to be drawn by the black-yellow theme's menu rule (views/PillMenuItemView):
 * its icon when the menu resource gave it none, whether it opens something (a › at the right end), and what
 * it does — which colours its icon. Menu items carry none of that, so this table supplies it by item id.
 *
 * Every menu item gets an icon in an instructive colour (白い熊, 2026-11-17): frame and label stay in the
 * menu colour — the warning colour for a destructive row — while the icon tells what the row does:
 * call green, message blue, contact cyan, copy/share orange, view/open/settings yellow, block magenta,
 * delete red. Each is a settable colour role (UI settings → Menus).
 *
 * A new menu item: add a line to [ROWS]. One without a line still becomes a pill, with a view-coloured
 * icon column and no ›.
 */
@SuppressLint("RestrictedApi")
object MenuPills {
    const val DISABLED_ALPHA = 97 // 38 %
    const val TOGGLE_HEIGHT_DP = 36 // a toggle row: the switch and little more
    const val PILL_MARGIN_DP = 3 // above and below a pill
    const val SIDE_MARGIN_DP = 8 // from the pill to the edge of the menu
    private const val DIVIDER_BLEND = 0.7f // 70 % of the way from the background to the accent
    private const val SWITCH_OFF_BLEND = 0.55f

    /** What a row does; decides its icon colour. */
    enum class Role(val slot: ThemeSlot) {
        CALL(ThemeSlot.MENU_ICON_CALL),
        MESSAGE(ThemeSlot.MENU_ICON_MESSAGE),
        CONTACT(ThemeSlot.MENU_ICON_CONTACT),
        SHARE(ThemeSlot.MENU_ICON_SHARE),
        VIEW(ThemeSlot.MENU_ICON_VIEW),
        BLOCK(ThemeSlot.MENU_ICON_BLOCK),
        DELETE(ThemeSlot.MENU_WARNING),
    }

    class Row(val role: Role, val opens: Boolean = false, val icon: Int = 0)

    private val ROWS: Map<Int, Row> = mapOf(
        // main screen ⋮
        R.id.ui_settings to Row(Role.VIEW, opens = true, icon = CR.drawable.ic_settings_cog_vector),
        R.id.open_groups to Row(Role.CONTACT, opens = true, icon = CR.drawable.ic_people_vector),
        R.id.select_favorites to Row(Role.VIEW, icon = CR.drawable.ic_select_all_vector),
        R.id.set_favorite_numbers to Row(Role.CALL, opens = true, icon = CR.drawable.ic_phone_vector),
        R.id.sort to Row(Role.VIEW, opens = true),
        R.id.filter to Row(Role.VIEW, opens = true),
        R.id.dialpad to Row(Role.CALL, opens = true),
        R.id.change_view_type to Row(Role.VIEW, opens = true),
        R.id.column_count to Row(Role.VIEW, opens = true),
        R.id.settings to Row(Role.VIEW, opens = true),
        R.id.about to Row(Role.VIEW, opens = true),
        R.id.more_apps_from_us to Row(Role.VIEW, opens = true, icon = CR.drawable.ic_apps_vector),
        // selection menus
        R.id.cab_edit to Row(Role.CONTACT, opens = true),
        R.id.cab_add_to_favorites to Row(Role.CONTACT),
        R.id.cab_add_to_group to Row(Role.CONTACT, opens = true),
        R.id.cab_share to Row(Role.SHARE, opens = true),
        R.id.cab_send_sms_to_contacts to Row(Role.MESSAGE, opens = true),
        R.id.cab_send_email_to_contacts to Row(Role.MESSAGE, opens = true),
        R.id.cab_create_shortcut to Row(Role.SHARE, icon = CR.drawable.ic_plus_vector),
        R.id.cab_set_default_sim to Row(Role.CALL, opens = true),
        R.id.cab_set_rin_account to Row(Role.CALL, opens = true, icon = CR.drawable.ic_phone_vector),
        R.id.cab_make_visible to Row(Role.VIEW, icon = CR.drawable.ic_unhide_vector),
        R.id.cab_make_private_only to Row(Role.BLOCK, icon = CR.drawable.ic_hide_vector),
        R.id.cab_select_all to Row(Role.VIEW),
        R.id.cab_delete to Row(Role.DELETE),
        R.id.cab_remove to Row(Role.DELETE),
        R.id.cab_rename to Row(Role.CONTACT, opens = true),
        // contact screens
        R.id.save to Row(Role.CONTACT),
        R.id.edit to Row(Role.CONTACT, opens = true),
        R.id.share to Row(Role.SHARE, opens = true),
        R.id.delete to Row(Role.DELETE),
        R.id.open_with to Row(Role.VIEW, opens = true, icon = CR.drawable.ic_set_as_vector),
        R.id.visible_to_other_apps to Row(Role.VIEW, icon = CR.drawable.ic_unhide_vector),
        R.id.manage_visible_fields to Row(Role.VIEW, opens = true, icon = CR.drawable.ic_view_contact_details_vector),
        // long-press on a 臨N badge that can do callback
        R.id.rin_callback to Row(Role.CALL, icon = R.drawable.ic_call_received_vector),
        R.id.rin_call to Row(Role.CALL, icon = CR.drawable.ic_phone_vector),
        R.id.rin_set_account to Row(Role.CALL, opens = true, icon = CR.drawable.ic_people_vector),
        // group screen
        R.id.assign_ringtone_to_group to Row(Role.VIEW, opens = true),
        R.id.send_sms_to_group to Row(Role.MESSAGE, opens = true),
        R.id.send_email_to_group to Row(Role.MESSAGE, opens = true),
    )

    private fun row(item: MenuItem) = ROWS[item.itemId]

    /** Frame and label of every row; the same slot the popup's text always had. */
    fun accent(context: Context) = context.themeColor(ThemeSlot.SEARCH_MENU_TEXT)

    fun warning(context: Context) = context.themeColor(ThemeSlot.MENU_WARNING)

    fun iconColor(context: Context, item: MenuItem) = context.themeColor((row(item)?.role ?: Role.VIEW).slot)

    /** The table icon of an item that brought none, or 0. */
    fun iconFor(item: MenuItem) = row(item)?.icon ?: 0

    fun opens(item: MenuItem): Boolean =
        item.hasSubMenu() || row(item)?.opens == true || item.title?.toString()?.trim()?.endsWith("…") == true

    fun isDestructive(item: MenuItem) = row(item)?.role == Role.DELETE

    /** The disabled title line a menu opens with. */
    private fun isHeader(item: MenuItem) = !item.isEnabled && item.itemId == 0 && !item.hasSubMenu()

    /** A row with a switch: checkable, and not one of an exclusive (radio) group. */
    fun isToggle(item: MenuItemImpl) = item.isCheckable && !item.isExclusiveCheckable

    /** An action row: everything that is not a toggle, a radio choice or a header. */
    fun isPill(item: MenuItemImpl) = !item.isCheckable && !isHeader(item)

    /**
     * Whether the rows of this menu keep an icon column. A list of pure choices with no icon anywhere stays
     * iconless; as soon as one row has an icon every row keeps the column, so the labels share one left edge.
     */
    fun anyIcon(menu: MenuBuilder?): Boolean {
        menu ?: return true
        return try {
            menu.visibleItems.any { it.icon != null || iconFor(it) != 0 }
        } catch (ignored: Throwable) {
            false
        }
    }

    /** The line between two groups: clearly there, quieter than a pill frame. Derived, never a constant. */
    fun divider(context: Context) = ColorUtils.blendARGB(context.getProperBackgroundColor(), accent(context), DIVIDER_BLEND)

    /** A switch that is off: an opaque darker accent, never a faded one. */
    fun switchOff(context: Context) = ColorUtils.blendARGB(context.getProperBackgroundColor(), accent(context), SWITCH_OFF_BLEND)
}
