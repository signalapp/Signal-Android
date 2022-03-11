package org.thoughtcrime.securesms.groups.ui.invitesandrequests.requesting;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

final class RequestConfirmationDialog {

  private RequestConfirmationDialog() {
  }

  /**
   * Confirms that you want to approve a request to join the group.
   */
  public static AlertDialog showApprove(@NonNull Context context,
                                        @NonNull Recipient requester,
                                        @NonNull Runnable onApprove)
  {
    return new MaterialAlertDialogBuilder(context)
        .setMessage(context.getString(R.string.RequestConfirmationDialog_add_s_to_the_group,
                                      requester.getDisplayName(context)))
        .setPositiveButton(R.string.RequestConfirmationDialog_add, (dialog, which) -> onApprove.run())
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  /**
   * Confirms that you want to deny a request to join the group.
   */
  public static AlertDialog showDeny(@NonNull Context context,
                                     @NonNull Recipient requester,
                                     boolean linkEnabled,
                                     @NonNull Runnable onDeny)
  {
    String message = linkEnabled ? context.getString(R.string.RequestConfirmationDialog_deny_request_from_s_they_will_not_be_able_to_request, requester.getDisplayName(context))
                                 : context.getString(R.string.RequestConfirmationDialog_deny_request_from_s, requester.getDisplayName(context));

    return new MaterialAlertDialogBuilder(context)
        .setMessage(message)
        .setPositiveButton(R.string.RequestConfirmationDialog_deny, (dialog, which) -> onDeny.run())
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

}
