package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class CreateClosedGroupLoader(context: Context) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        return contacts.filter { contact ->
            !contact.recipient.isGroupRecipient && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }
    }
}