package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public final class GroupErrors {
  private GroupErrors() {
  }

  public static @StringRes int getUserDisplayMessage(@Nullable GroupChangeFailureReason failureReason) {
    if (failureReason == null) {
      return R.string.ManageGroupActivity_failed_to_update_the_group;
    }

    switch (failureReason) {
      case NO_RIGHTS               : return R.string.ManageGroupActivity_you_dont_have_the_rights_to_do_this;
      case NOT_GV2_CAPABLE         : return R.string.ManageGroupActivity_not_capable;
      case NOT_ANNOUNCEMENT_CAPABLE: return R.string.ManageGroupActivity_not_announcement_capable;
      case NOT_A_MEMBER            : return R.string.ManageGroupActivity_youre_not_a_member_of_the_group;
      case BUSY                    : return R.string.ManageGroupActivity_failed_to_update_the_group_please_retry_later;
      case NETWORK                 : return R.string.ManageGroupActivity_failed_to_update_the_group_due_to_a_network_error_please_retry_later;
      default                      : return R.string.ManageGroupActivity_failed_to_update_the_group;
    }
  }
}
