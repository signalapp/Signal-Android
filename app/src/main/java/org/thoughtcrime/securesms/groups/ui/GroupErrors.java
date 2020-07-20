package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public final class GroupErrors {
  private GroupErrors() {
  }

  public static @StringRes int getUserDisplayMessage(@NonNull GroupChangeFailureReason failureReason) {
    switch (failureReason) {
      case NO_RIGHTS   : return R.string.ManageGroupActivity_you_dont_have_the_rights_to_do_this;
      case NOT_CAPABLE : return R.string.ManageGroupActivity_not_capable;
      case NOT_A_MEMBER: return R.string.ManageGroupActivity_youre_not_a_member_of_the_group;
      case BUSY        : return R.string.ManageGroupActivity_failed_to_update_the_group_please_retry_later;
      case NETWORK     : return R.string.ManageGroupActivity_failed_to_update_the_group_due_to_a_network_error_please_retry_later;
      default          : return R.string.ManageGroupActivity_failed_to_update_the_group;
    }
  }
}
