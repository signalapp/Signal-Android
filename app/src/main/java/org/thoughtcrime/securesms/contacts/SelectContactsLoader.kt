package org.thoughtcrime.securesms.contacts

import android.content.Context
import org.thoughtcrime.securesms.util.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class SelectContactsLoader(context: Context, val usersToExclude: Set<String>) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        return contacts.filter { contact ->
            !contact.isGroupRecipient && !usersToExclude.contains(contact.address.toString())
        }.map {
            it.address.toString()
        }
    }
}