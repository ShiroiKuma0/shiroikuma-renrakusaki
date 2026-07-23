package org.fossify.contacts.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyLinearLayoutManager
import org.fossify.contacts.activities.EditContactActivity
import org.fossify.contacts.activities.InsertOrEditContactActivity
import org.fossify.contacts.activities.MainActivity
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.adapters.ContactsAdapter
import org.fossify.contacts.databinding.FragmentContactsBinding
import org.fossify.contacts.databinding.FragmentLettersLayoutBinding
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.viewContact
import org.fossify.contacts.helpers.LOCATION_CONTACTS_TAB
import org.fossify.contacts.interfaces.RefreshContactsListener

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet) {

    private lateinit var binding: FragmentContactsBinding
    private var lastContacts = listOf<Contact>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentContactsBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun fabClicked() {
        activity?.hideKeyboard()
        Intent(context, EditContactActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    override fun placeholderClicked() {
        if (activity is MainActivity) {
            (activity as MainActivity).showFilterDialog()
        } else if (activity is InsertOrEditContactActivity) {
            (activity as InsertOrEditContactActivity).showFilterDialog()
        }
    }

    fun setupContactsAdapter(contacts: List<Contact>) {
        lastContacts = contacts
        setupListLayoutManager()
        setupViewVisibility(contacts.isNotEmpty())
        val currAdapter = innerBinding.fragmentList.adapter

        if (currAdapter == null || forceListRedraw) {
            forceListRedraw = false
            val location = LOCATION_CONTACTS_TAB

            ContactsAdapter(
                activity = activity as SimpleActivity,
                contactItems = contacts.toMutableList(),
                refreshListener = activity as RefreshContactsListener,
                location = location,
                removeListener = null,
                recyclerView = innerBinding.fragmentList,
                enableDrag = false,
                itemClick = {
                    (activity as RefreshContactsListener).contactClicked(it as Contact)
                },
                profileIconClick = {
                    activity?.viewContact(it as Contact)
                },
                groupBySections = isGrouped(),
                detailMode = isDetailMode()
            ).apply {
                innerBinding.fragmentList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).apply {
                startNameWithSurname = context.config.startNameWithSurname
                showPhoneNumbers = context.config.showPhoneNumbers
                showContactThumbnails = context.config.showContactThumbnails
                updateItems(contacts)
            }
        }
    }

    // The "contacts per row" toolbar buttons rebuild the list with a new column count.
    fun columnCountChanged() {
        forceListRedraw = true
        setupContactsAdapter(lastContacts)
    }

    // Letter sections apply only to the real Contacts tab — the contact picker (InsertOrEdit) stays a
    // flat list, where folded-away contacts would only get in the way.
    private fun isGrouped() = activity is MainActivity && context.config.contactsListGrouped

    // 詳 detail rows likewise only on the real Contacts tab; they force a single column.
    private fun isDetailMode() = activity is MainActivity && context.config.contactsListDetailMode

    // 1 column = list view (with the letter fastscroller); 2–4 columns = a grid (fastscroller hidden).
    // Grouped mode hides the fastscroller too — its position→letter mapping ignores header rows, and the
    // section headers themselves are the letter index. Only swap the layout manager when it actually
    // changes, so a normal refresh keeps the scroll position.
    private fun setupListLayoutManager() {
        val columns = if (isDetailMode()) 1 else context.config.contactsListColumns
        val current = innerBinding.fragmentList.layoutManager
        if (columns > 1) {
            innerBinding.letterFastscroller.beGone()
            if (current !is MyGridLayoutManager || current.spanCount != columns) {
                innerBinding.fragmentList.layoutManager = MyGridLayoutManager(context, columns).apply {
                    // Section headers span the full width; contacts flow in the columns beneath them.
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            val adapter = innerBinding.fragmentList.adapter as? ContactsAdapter
                            return if (adapter?.isSectionAt(position) == true) columns else 1
                        }
                    }
                }
            }
        } else {
            innerBinding.letterFastscroller.beVisibleIf(!isGrouped())
            if (current !is MyLinearLayoutManager) {
                innerBinding.fragmentList.layoutManager = MyLinearLayoutManager(context)
            }
        }
    }
}
