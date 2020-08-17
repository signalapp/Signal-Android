package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.ContactUtilities
import org.thoughtcrime.securesms.util.AsyncLoader

class EditClosedGroupLoader(
        context: Context,
        val groupID: String
) : AsyncLoader<List<String>>(context) {

    override fun loadInBackground(): List<String> {
        val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, true)
        return members.map {
            it.address.toPhoneString()
        }
        /* TODO:Load admins in the process here */
    }
}