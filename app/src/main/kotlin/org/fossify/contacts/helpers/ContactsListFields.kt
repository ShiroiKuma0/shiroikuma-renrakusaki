package org.fossify.contacts.helpers

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.config

// One displayable field (or subfield) that can appear in a contacts-list row.
// `slot` carries its per-element styling (font / weight / size / color) via the existing theme system;
// `extract` returns the contact's first/primary value for the field ("" => the field is skipped).
enum class RowField(
    val key: String,
    @StringRes val labelRes: Int,
    val slot: ThemeSlot,
    val extract: (Contact, Context) -> String,
) {
    DISPLAY_NAME(
        "display_name", R.string.field_display_name, ThemeSlot.ROW_DISPLAY_NAME,
        { c, _ -> c.getNameToDisplay() },
    ),
    // "Surname, Firstname" as one field; falls back to the display name when the contact has neither
    // (e.g. company-only contacts), so a row built on this field never goes blank.
    SURNAME_FIRST(
        "surname_first", R.string.field_surname_first, ThemeSlot.ROW_SURNAME_FIRST,
        { c, _ ->
            listOf(c.surname, c.firstName).filter { it.isNotEmpty() }.joinToString(", ")
                .ifEmpty { c.getNameToDisplay() }
        },
    ),
    PREFIX("prefix", R.string.field_prefix, ThemeSlot.ROW_PREFIX, { c, _ -> c.prefix }),
    FIRST_NAME("first_name", R.string.field_first_name, ThemeSlot.ROW_FIRST_NAME, { c, _ -> c.firstName }),
    MIDDLE_NAME("middle_name", R.string.field_middle_name, ThemeSlot.ROW_MIDDLE_NAME, { c, _ -> c.middleName }),
    SURNAME("surname", R.string.field_surname, ThemeSlot.ROW_SURNAME, { c, _ -> c.surname }),
    SUFFIX("suffix", R.string.field_suffix, ThemeSlot.ROW_SUFFIX, { c, _ -> c.suffix }),
    NICKNAME("nickname", R.string.field_nickname, ThemeSlot.ROW_NICKNAME, { c, _ -> c.nickname }),
    PHONE("phone", R.string.field_phone, ThemeSlot.ROW_PHONE, { c, ctx -> primaryPhone(c, ctx) }),
    EMAIL("email", R.string.field_email, ThemeSlot.ROW_EMAIL, { c, _ -> c.emails.firstOrNull()?.value.orEmpty() }),
    ADDRESS(
        "address", R.string.field_address, ThemeSlot.ROW_ADDRESS,
        { c, _ -> c.addresses.firstOrNull()?.value.orEmpty() },
    ),
    ADDRESS_STREET(
        "address_street", R.string.field_address_street, ThemeSlot.ROW_ADDRESS_STREET,
        { c, _ -> c.addresses.firstOrNull()?.street.orEmpty() },
    ),
    ADDRESS_CITY(
        "address_city", R.string.field_address_city, ThemeSlot.ROW_ADDRESS_CITY,
        { c, _ -> c.addresses.firstOrNull()?.city.orEmpty() },
    ),
    ADDRESS_REGION(
        "address_region", R.string.field_address_region, ThemeSlot.ROW_ADDRESS_REGION,
        { c, _ -> c.addresses.firstOrNull()?.region.orEmpty() },
    ),
    ADDRESS_POSTCODE(
        "address_postcode", R.string.field_address_postcode, ThemeSlot.ROW_ADDRESS_POSTCODE,
        { c, _ -> c.addresses.firstOrNull()?.postcode.orEmpty() },
    ),
    ADDRESS_COUNTRY(
        "address_country", R.string.field_address_country, ThemeSlot.ROW_ADDRESS_COUNTRY,
        { c, _ -> c.addresses.firstOrNull()?.country.orEmpty() },
    ),
    COMPANY("company", R.string.field_company, ThemeSlot.ROW_COMPANY, { c, _ -> c.organization.company }),
    POSITION("position", R.string.field_position, ThemeSlot.ROW_POSITION, { c, _ -> c.organization.jobPosition }),
    WEBSITE("website", R.string.field_website, ThemeSlot.ROW_WEBSITE, { c, _ -> c.websites.firstOrNull().orEmpty() }),
    IM("im", R.string.field_im, ThemeSlot.ROW_IM, { c, _ -> c.IMs.firstOrNull()?.value.orEmpty() }),
    BIRTHDAY(
        "birthday", R.string.field_birthday, ThemeSlot.ROW_BIRTHDAY,
        { c, _ -> c.birthdays.firstOrNull().orEmpty() },
    ),
    ANNIVERSARY(
        "anniversary", R.string.field_anniversary, ThemeSlot.ROW_ANNIVERSARY,
        { c, _ -> c.anniversaries.firstOrNull().orEmpty() },
    ),
    NOTE("note", R.string.field_note, ThemeSlot.ROW_NOTE, { c, _ -> c.notes }),
    GROUPS("groups", R.string.field_groups, ThemeSlot.ROW_GROUPS, { c, _ -> c.groups.joinToString(", ") { it.title } }),
    CONTACT_SOURCE("contact_source", R.string.field_contact_source, ThemeSlot.ROW_CONTACT_SOURCE, { c, _ -> c.source });

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

private fun primaryPhone(contact: Contact, context: Context): String {
    val number = contact.phoneNumbers.firstOrNull { it.isPrimary }?.value
        ?: contact.phoneNumbers.firstOrNull()?.value ?: return ""
    return if (context.config.formatPhoneNumbers) number.formatPhoneNumber() else number
}

// A single row in the editor / layout: a field, whether it is shown, and whether it shares the
// previous shown field's line (true => sits as a column to its right, false => starts a new line).
data class RowFieldEntry(val field: RowField, var checked: Boolean, var sameLine: Boolean)

// Parse / serialize the contacts-list layout config, and the built-in default.
object ContactsListConfig {
    private const val ENTRY_SEP = "|"
    private const val PART_SEP = ":"

    // Default: "Lastname, Firstname" on the first line, phone on the second; everything else off
    // (the stock display-name field stays available but unchecked).
    fun defaultEntries(): List<RowFieldEntry> {
        val onByDefault = listOf(RowField.SURNAME_FIRST, RowField.PHONE)
        val result = ArrayList<RowFieldEntry>()
        onByDefault.forEach { result.add(RowFieldEntry(it, checked = true, sameLine = false)) }
        RowField.entries.filter { it !in onByDefault }.forEach {
            result.add(RowFieldEntry(it, checked = false, sameLine = false))
        }
        return result
    }

    fun parse(stored: String): List<RowFieldEntry> {
        if (stored.isBlank()) {
            return defaultEntries()
        }

        val seen = LinkedHashMap<RowField, RowFieldEntry>()
        stored.split(ENTRY_SEP).forEach { token ->
            val parts = token.split(PART_SEP)
            val field = parts.getOrNull(0)?.let { RowField.fromKey(it) } ?: return@forEach
            val checked = parts.getOrNull(1) == "1"
            val sameLine = parts.getOrNull(2) == "1"
            seen[field] = RowFieldEntry(field, checked, sameLine)
        }

        // Add any catalog fields missing from storage (e.g. introduced in a later version), unchecked —
        // "Lastname, Firstname" goes to the top of the list, everything else to the end. Once the user
        // reorders and saves, the stored order wins.
        val result = seen.values.toMutableList()
        RowField.entries.forEach { field ->
            if (field !in seen) {
                val entry = RowFieldEntry(field, checked = false, sameLine = false)
                if (field == RowField.SURNAME_FIRST) {
                    result.add(0, entry)
                } else {
                    result.add(entry)
                }
            }
        }
        return result
    }

    fun serialize(entries: List<RowFieldEntry>): String = entries.joinToString(ENTRY_SEP) { entry ->
        "${entry.field.key}$PART_SEP${if (entry.checked) 1 else 0}$PART_SEP${if (entry.sameLine) 1 else 0}"
    }
}

// One-time switch of the default name field to "Lastname, Firstname" for layouts saved before it
// existed: where the stored layout still shows the stock display name, SURNAME_FIRST takes its place
// (position and line arrangement included) and DISPLAY_NAME is unchecked. Skipped when the user
// already enabled SURNAME_FIRST themselves, and never repeated, so manual changes stick afterwards.
fun Context.applySurnameFirstDefaultIfNeeded() {
    if (config.surnameFirstDefaultApplied) {
        return
    }
    config.surnameFirstDefaultApplied = true

    if (config.contactsListFields.isBlank()) {
        return // fresh install: defaultEntries() already leads with SURNAME_FIRST checked
    }

    val entries = ContactsListConfig.parse(config.contactsListFields).toMutableList()
    val surnameFirst = entries.first { it.field == RowField.SURNAME_FIRST }
    val displayName = entries.first { it.field == RowField.DISPLAY_NAME }
    if (surnameFirst.checked || !displayName.checked) {
        return
    }

    surnameFirst.checked = true
    surnameFirst.sameLine = displayName.sameLine
    displayName.checked = false
    displayName.sameLine = false

    entries.remove(surnameFirst)
    entries.add(entries.indexOf(displayName), surnameFirst)

    config.contactsListFields = ContactsListConfig.serialize(entries)
    config.contactsListRevision += 1
}
