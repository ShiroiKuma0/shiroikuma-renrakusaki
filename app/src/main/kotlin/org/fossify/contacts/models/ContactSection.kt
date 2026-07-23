package org.fossify.contacts.models

// A letter-section header row in the grouped Contacts list. [title] is the bucket key (first letter of
// the sort name, or "#") and also the persistence key for the section's expanded/folded state.
// [showTopDivider]: full-width separators frame only unfolded content — a header draws one on top when
// its own section is open, or to close off an open section directly above it; folded headers stacked on
// folded headers get none.
data class ContactSection(
    val title: String,
    val count: Int,
    val expanded: Boolean,
    val showTopDivider: Boolean = false,
)
