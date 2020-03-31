package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

public final class LeaveGroupDialog {

  private LeaveGroupDialog() {
  }

  public static void handleLeavePushGroup(@NonNull Context context,
                                          @NonNull Lifecycle lifecycle,
                                          @NonNull GroupId.Push groupId,
                                          @Nullable Runnable onSuccess)
  {
    new AlertDialog.Builder(context)
                   .setTitle(context.getString(R.string.ConversationActivity_leave_group))
                   .setIconAttribute(R.attr.dialog_info_icon)
                   .setCancelable(true)
                   .setMessage(context.getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group))
                   .setPositiveButton(R.string.yes, (dialog, which) ->
                      SimpleTask.run(
                        lifecycle,
                        () -> GroupManager.leaveGroup(context, groupId),
                        (success) -> {
                          if (success) {
                            if (onSuccess != null) onSuccess.run();
                          } else {
                            Toast.makeText(context, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
                          }
                        }))
                   .setNegativeButton(R.string.no, null)
                   .show();
  }
}
