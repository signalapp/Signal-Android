package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class EditClosedGroupLoader(val groupID: String, context: Context) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, false)
        return members.map {
            it.address.toPhoneString()
        }
    }

/* For loading contacts for Add members, and loading admins from group list

    override fun loadContactsInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        // Only show the master devices of the users we are friends with
        return contacts.filter { contact ->
            !contact.recipient.isGroupRecipient && contact.isFriend && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }
    }
    override fun loadAdminsInBackground(): List<String> {
        val contacts = ContactUtilities.getAllContacts(context)
        // Only show the master devices of the users we are friends with
        return contacts.filter { contact ->
            !contact.recipient.isGroupRecipient && contact.isFriend && !contact.isOurDevice && !contact.isSlave
        }.map {
            it.recipient.address.toPhoneString()
        }
    }

 */
}