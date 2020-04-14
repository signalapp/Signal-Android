package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import org.thoughtcrime.securesms.loki.redesign.utilities.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class CreateClosedGroupLoader(context: Context) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        // Only show the master device of the users we are friends with
        return contacts.filter { contact ->
            !contact.recipient.isGroupRecipient && contact.isFriend && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }
    }
}