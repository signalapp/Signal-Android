package org.thoughtcrime.securesms.permissions;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.permissions.PermissionHandler.PermissionRequest;
import org.thoughtcrime.securesms.permissions.PermissionHandler.RationaleCallback;

import java.util.List;

public abstract class DialogRationalePermissionRequest extends PermissionRequest {
  private            Context context;
  @StringRes private int     rationaleTitle;
  @StringRes private int     rationaleMessage;

  public DialogRationalePermissionRequest(@NonNull Context context,
                                          @StringRes int rationaleTitle,
                                          @StringRes int rationaleMessage,
                                          @NonNull String... permissions)
  {
    super(permissions);
    this.context = context;
    this.rationaleTitle = rationaleTitle;
    this.rationaleMessage = rationaleMessage;
  }

  @Override public boolean onRationaleRequested(
      @NonNull final List<String> permissionsNeedingRationale,
      @NonNull final RationaleCallback callback)
  {
    final AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(rationaleTitle)
           .setMessage(rationaleMessage)
           .setNeutralButton(R.string.PermissionHandler_dialog_accept_button, new OnClickListener() {
             @Override public void onClick(DialogInterface dialog, int which) {
               callback.onRationaleFinished();
               dialog.dismiss();
             }
           })
           .show();
    return true;
  }
}
