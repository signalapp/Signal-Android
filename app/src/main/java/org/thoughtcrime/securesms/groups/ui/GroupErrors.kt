package org.thoughtcrime.securesms.groups.ui

import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

object GroupErrors {
  @JvmStatic
  @StringRes
  fun getUserDisplayMessage(failureReason: GroupChangeFailureReason?): Int {
    return when (failureReason) {
      GroupChangeFailureReason.NO_RIGHTS -> R.string.GroupErrors__you_dont_have_the_rights_to_do_this
      GroupChangeFailureReason.NOT_GV2_CAPABLE -> R.string.GroupErrors__not_capable
      GroupChangeFailureReason.NOT_ANNOUNCEMENT_CAPABLE -> R.string.GroupErrors__not_announcement_capable
      GroupChangeFailureReason.NOT_A_MEMBER -> R.string.GroupErrors__youre_not_a_member_of_the_group
      GroupChangeFailureReason.BUSY -> R.string.GroupErrors__failed_to_update_the_group_please_retry_later
      GroupChangeFailureReason.NETWORK -> R.string.GroupErrors__failed_to_update_the_group_due_to_a_network_error_please_retry_later
      GroupChangeFailureReason.GROUP_TERMINATED -> R.string.GroupErrors__the_group_was_terminated
      else -> R.string.GroupErrors__failed_to_update_the_group
    }
  }
}
