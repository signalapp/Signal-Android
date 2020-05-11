package org.thoughtcrime.securesms.loki.fragments

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.utilities.Contact
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AsyncLoader

sealed class ContactSelectionListItem {
    class Header(val name: String) : ContactSelectionListItem()
    class Contact(val recipient: Recipient) : ContactSelectionListItem()
}

class ContactSelectionListLoader(context: Context, val mode: Int, val filter: String?) : AsyncLoader<List<ContactSelectionListItem>>(context) {

    object DisplayMode {
        const val FLAG_FRIENDS = 1
        const val FLAG_CLOSED_GROUPS = 1 shl 1
        const val FLAG_OPEN_GROUPS = 1 shl 2
        const val FLAG_ALL = FLAG_FRIENDS or FLAG_CLOSED_GROUPS or FLAG_OPEN_GROUPS
    }

    private fun isFlagSet(flag: Int): Boolean {
        return mode and flag > 0
    }

    override fun loadInBackground(): List<ContactSelectionListItem> {
        val contacts = ContactUtilities.getAllContacts(context).filter {
            if (filter.isNullOrEmpty()) return@filter true
            it.recipient.toShortString().contains(filter.trim(), true) || it.recipient.address.serialize().contains(filter.trim(), true)
        }.sortedBy {
            it.recipient.toShortString()
        }
        val list = mutableListOf<ContactSelectionListItem>()
        if (isFlagSet(DisplayMode.FLAG_CLOSED_GROUPS)) {
            list.addAll(getClosedGroups(contacts))
        }
        if (isFlagSet(DisplayMode.FLAG_OPEN_GROUPS)) {
            list.addAll(getOpenGroups(contacts))
        }
        if (isFlagSet(DisplayMode.FLAG_FRIENDS)) {
            list.addAll(getFriends(contacts))
        }
        return list
    }

    private fun getFriends(contacts: List<Contact>): List<ContactSelectionListItem> {
        return getItems(contacts, context.getString(R.string.fragment_contact_selection_contacts_title)) {
            !it.recipient.isGroupRecipient && it.isFriend && !it.isOurDevice && !it.isSlave
        }
    }

    private fun getClosedGroups(contacts: List<Contact>): List<ContactSelectionListItem> {
        return getItems(contacts, context.getString(R.string.fragment_contact_selection_closed_groups_title)) {
            it.recipient.address.isClosedGroup
        }
    }

    private fun getOpenGroups(contacts: List<Contact>): List<ContactSelectionListItem> {
        return getItems(contacts, context.getString(R.string.fragment_contact_selection_open_groups_title)) {
            it.recipient.address.isOpenGroup
        }
    }

    private fun getItems(contacts: List<Contact>, title: String, contactFilter: (Contact) -> Boolean): List<ContactSelectionListItem> {
        val items = contacts.filter(contactFilter).map {
            ContactSelectionListItem.Contact(it.recipient)
        }
        if (items.isEmpty()) return listOf()
        val header = ContactSelectionListItem.Header(title)
        return listOf(header) + items
    }
}