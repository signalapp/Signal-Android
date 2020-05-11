package org.thoughtcrime.securesms.groups.ui.pendingmemberinvites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

final class InviteCancelConfirmationDialog {

  private InviteCancelConfirmationDialog() {
  }

  /**
   * Confirms that you want to cancel an invite that you sent.
   */
  static AlertDialog showOwnInviteCancelConfirmationDialog(@NonNull Context context,
                                                           @NonNull Recipient invitee,
                                                           @NonNull Runnable onCancel)
  {
    return new AlertDialog.Builder(context)
                          .setMessage(context.getString(R.string.GroupManagement_cancel_own_single_invite,
                                                        invitee.getDisplayName(context)))
                          .setPositiveButton(R.string.yes, (dialog, which) -> onCancel.run())
                          .setNegativeButton(R.string.no, null)
                          .show();
  }

  /**
   * Confirms that you want to cancel a number of invites that another member sent.
   */
  static AlertDialog showOthersInviteCancelConfirmationDialog(@NonNull Context context,
                                                              @NonNull Recipient inviter,
                                                              int numberOfInvitations,
                                                              @NonNull Runnable onCancel)
  {
    return new AlertDialog.Builder(context)
                          .setMessage(context.getResources().getQuantityString(R.plurals.GroupManagement_cancel_others_invites,
                                                                               numberOfInvitations,
                                                                               inviter.getDisplayName(context),
                                                                               numberOfInvitations))
                          .setPositiveButton(R.string.yes, (dialog, which) -> onCancel.run())
                          .setNegativeButton(R.string.no, null)
                          .show();
  }
}
