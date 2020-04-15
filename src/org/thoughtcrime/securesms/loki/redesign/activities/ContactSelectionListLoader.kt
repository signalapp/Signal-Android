package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.redesign.utilities.Contact
import org.thoughtcrime.securesms.loki.redesign.utilities.ContactUtilities
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AsyncLoader

sealed class ContactSelectionListLoaderItem {
  class Header(val name: String): ContactSelectionListLoaderItem()
  class Contact(val recipient: Recipient): ContactSelectionListLoaderItem()

}

class ContactSelectionListLoader(context: Context, val mode: Int, val filter: String?) : AsyncLoader<List<ContactSelectionListLoaderItem>>(context) {
  object DisplayMode {
    const val FLAG_FRIENDS = 1
    const val FLAG_CLOSED_GROUPS = 1 shl 1
    const val FLAG_OPEN_GROUPS = 1 shl 2
    const val FLAG_ALL = FLAG_FRIENDS or FLAG_CLOSED_GROUPS or FLAG_OPEN_GROUPS
  }

  private fun isFlagSet(flag: Int): Boolean {
    return mode and flag > 0
  }

  override fun loadInBackground(): List<ContactSelectionListLoaderItem> {
    val contacts = ContactUtilities.getAllContacts(context).filter {
      if (filter.isNullOrEmpty()) return@filter true

      it.recipient.toShortString().contains(filter.trim(), true) || it.recipient.address.serialize().contains(filter.trim(), true)
    }.sortedBy {
      it.recipient.toShortString()
    }

    val list = mutableListOf<ContactSelectionListLoaderItem>()
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

  private fun getFriends(contacts: List<Contact>): List<ContactSelectionListLoaderItem> {
    return getItems(contacts, context.getString(R.string.ContactSelectionListLoader_contacts)) {
      !it.recipient.isGroupRecipient && it.isFriend && !it.isOurDevice && !it.isSlave
    }
  }

  private fun getClosedGroups(contacts: List<Contact>): List<ContactSelectionListLoaderItem> {
    return getItems(contacts, context.getString(R.string.ContactSelectionListLoader_closed_groups)) {
      it.recipient.address.isSignalGroup
    }
  }

  private fun getOpenGroups(contacts: List<Contact>): List<ContactSelectionListLoaderItem> {
    return getItems(contacts, context.getString(R.string.ContactSelectionListLoader_open_groups)) {
      it.recipient.address.isPublicChat
    }
  }

  private fun getItems(contacts: List<Contact>, title: String, contactFilter: (Contact) -> Boolean): List<ContactSelectionListLoaderItem> {
    val items = contacts.filter(contactFilter).map {
      ContactSelectionListLoaderItem.Contact(it.recipient)
    }
    if (items.isEmpty()) return listOf()

    val header = ContactSelectionListLoaderItem.Header(title)
    return listOf(header) + items
  }
}