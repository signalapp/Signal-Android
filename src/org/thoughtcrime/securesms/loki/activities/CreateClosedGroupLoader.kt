package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class CreateClosedGroupLoader(context: Context) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        // Only show the master devices of the users we are friends with
        return contacts.filter { contact ->
            !contact.recipient.isGroupRecipient && contact.isFriend && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }
    }
}