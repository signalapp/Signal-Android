package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Groups in common helper class
 */
data class GroupsInCommon(
  private val total: Int,
  private val names: List<String>
) {
  fun toDisplayText(context: Context): String {
    return when (total) {
      0 -> {
        Log.w(TAG, "Member with no groups in common!")
        return ""
      }
      1 -> context.getString(R.string.MessageRequestProfileView_member_of_one_group, names[0])
      2 -> context.getString(R.string.MessageRequestProfileView_member_of_two_groups, names[0], names[1])
      else -> context.getString(
        R.string.MessageRequestProfileView_member_of_many_groups,
        names[0],
        names[1],
        context.resources.getQuantityString(R.plurals.MessageRequestProfileView_member_of_d_additional_groups, total - 2, total - 2)
      )
    }
  }

  companion object {
    private val TAG = Log.tag(GroupsInCommon::class.java)
  }
}
