package org.thoughtcrime.securesms.groups.ui;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.groups.GroupChangeException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.ui.chooseadmin.ChooseNewAdminActivity;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.io.IOException;
import java.util.List;

public final class LeaveGroupDialog {

  private static final String TAG = Log.tag(LeaveGroupDialog.class);

  @NonNull  private final FragmentActivity activity;
  @NonNull  private final GroupId.Push     groupId;
  @Nullable private final Runnable         onSuccess;

  public static void handleLeavePushGroup(@NonNull FragmentActivity activity,
                                          @NonNull GroupId.Push groupId,
                                          @Nullable Runnable onSuccess) {
    new LeaveGroupDialog(activity, groupId, onSuccess).show();
  }

  private LeaveGroupDialog(@NonNull FragmentActivity activity,
                           @NonNull GroupId.Push groupId,
                           @Nullable Runnable onSuccess) {
    this.activity  = activity;
    this.groupId   = groupId;
    this.onSuccess = onSuccess;
  }

  public void show() {
    if (!groupId.isV2()) {
      showLeaveDialog();
      return;
    }

    SimpleTask.run(activity.getLifecycle(), () -> {
      GroupDatabase.V2GroupProperties groupProperties = DatabaseFactory.getGroupDatabase(activity)
                                                                       .getGroup(groupId)
                                                                       .transform(GroupDatabase.GroupRecord::requireV2GroupProperties)
                                                                       .orNull();

      if (groupProperties != null && groupProperties.isAdmin(Recipient.self())) {
        List<Recipient> otherMemberRecipients = groupProperties.getMemberRecipients(GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        long            otherAdminsCount      = Stream.of(otherMemberRecipients).filter(groupProperties::isAdmin).count();

        return otherAdminsCount == 0 && !otherMemberRecipients.isEmpty();
      }

      return false;
    }, mustSelectNewAdmin -> {
      if (mustSelectNewAdmin) {
        showSelectNewAdminDialog();
      } else {
        showLeaveDialog();
      }
    });
  }

  private void showSelectNewAdminDialog() {
    new AlertDialog.Builder(activity)
                   .setTitle(R.string.ConversationActivity_choose_new_admin)
                   .setMessage(R.string.ConversationActivity_before_you_leave_you_must_choose_at_least_one_new_admin_for_this_group)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(R.string.ConversationActivity_choose_admin, (d,w) -> activity.startActivity(ChooseNewAdminActivity.createIntent(activity, groupId.requireV2())))
                   .show();
  }

  private void showLeaveDialog() {
    new AlertDialog.Builder(activity)
                   .setTitle(R.string.ConversationActivity_leave_group)
                   .setIconAttribute(R.attr.dialog_info_icon)
                   .setCancelable(true)
                   .setMessage(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group)
                   .setPositiveButton(R.string.yes, (dialog, which) -> {
                     SimpleProgressDialog.DismissibleDialog progressDialog = SimpleProgressDialog.showDelayed(activity);
                     SimpleTask.run(activity.getLifecycle(), this::leaveGroup, result -> {
                       progressDialog.dismiss();
                       handleLeaveGroupResult(result);
                     });
                   })
                   .setNegativeButton(R.string.no, null)
                   .show();
  }

  private @NonNull GroupChangeResult leaveGroup() {
    try {
      GroupManager.leaveGroup(activity, groupId);
      return GroupChangeResult.SUCCESS;
    } catch (GroupChangeException | IOException e) {
      Log.w(TAG, e);
      return GroupChangeResult.failure(GroupChangeFailureReason.fromException(e));
    }
  }

  private void handleLeaveGroupResult(@NonNull GroupChangeResult result) {
    if (result.isSuccess()) {
      if (onSuccess != null) onSuccess.run();
    } else {
      Toast.makeText(activity, GroupErrors.getUserDisplayMessage(result.getFailureReason()), Toast.LENGTH_LONG).show();
    }
  }
}
