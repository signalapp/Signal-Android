package org.thoughtcrime.securesms.contacts

import android.content.Context
import org.thoughtcrime.securesms.util.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class SelectContactsLoader(context: Context, private val usersToExclude: Set<String>) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        return contacts.filter {
            !it.isGroupRecipient && !usersToExclude.contains(it.address.toString()) && it.hasApprovedMe()
        }.map {
            it.address.toString()
        }
    }
}