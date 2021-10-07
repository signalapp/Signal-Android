package org.thoughtcrime.securesms.groups

import android.content.Context
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.AsyncLoader

class EditClosedGroupLoader(context: Context, val groupID: String) : AsyncLoader<EditClosedGroupActivity.GroupMembers>(context) {

    override fun loadInBackground(): EditClosedGroupActivity.GroupMembers {
        val groupDatabase = DatabaseComponent.get(context).groupDatabase()
        val members = groupDatabase.getGroupMembers(groupID, true)
        val zombieMembers = groupDatabase.getGroupZombieMembers(groupID)
        return EditClosedGroupActivity.GroupMembers(
                members.map {
                    it.address.toString()
                },
                zombieMembers.map {
                    it.address.toString()
                }
        )
    }
}