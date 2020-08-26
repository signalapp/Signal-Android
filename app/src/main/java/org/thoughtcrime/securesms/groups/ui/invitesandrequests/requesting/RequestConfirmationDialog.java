package org.thoughtcrime.securesms.groups.ui.invitesandrequests.requesting;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

final class RequestConfirmationDialog {

  private RequestConfirmationDialog() {
  }

  /**
   * Confirms that you want to approve or deny a request to join the group depending on
   * {@param approve}.
   */
  static AlertDialog show(@NonNull Context context,
                          @NonNull Recipient requester,
                          boolean approve,
                          @NonNull Runnable onApproveOrDeny)
  {
    if (approve) {
      return showRequestApproveConfirmationDialog(context, requester, onApproveOrDeny);
    } else {
      return showRequestDenyConfirmationDialog(context, requester, onApproveOrDeny);
    }
  }

  /**
   * Confirms that you want to approve a request to join the group.
   */
  private static AlertDialog showRequestApproveConfirmationDialog(@NonNull Context context,
                                                                  @NonNull Recipient requester,
                                                                  @NonNull Runnable onApprove)
  {
    return new AlertDialog.Builder(context)
                          .setMessage(context.getString(R.string.RequestConfirmationDialog_add_s_to_the_group,
                                                        requester.getDisplayName(context)))
                          .setPositiveButton(R.string.RequestConfirmationDialog_add, (dialog, which) -> onApprove.run())
                          .setNegativeButton(android.R.string.cancel, null)
                          .show();
  }

  /**
   * Confirms that you want to deny a request to join the group.
   */
  private static AlertDialog showRequestDenyConfirmationDialog(@NonNull Context context,
                                                               @NonNull Recipient requester,
                                                               @NonNull Runnable onDeny)
  {
    return new AlertDialog.Builder(context)
                          .setMessage(context.getString(R.string.RequestConfirmationDialog_deny_request_from_s,
                                                        requester.getDisplayName(context)))
                          .setPositiveButton(R.string.RequestConfirmationDialog_deny, (dialog, which) -> onDeny.run())
                          .setNegativeButton(android.R.string.cancel, null)
                          .show();
  }

}
