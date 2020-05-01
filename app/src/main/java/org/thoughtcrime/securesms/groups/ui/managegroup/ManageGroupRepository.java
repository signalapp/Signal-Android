package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupAccessControl;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

final class ManageGroupRepository {

  private static final String TAG = Log.tag(ManageGroupRepository.class);

  private final Context         context;
  private final GroupId         groupId;
  private final ExecutorService executor;

  ManageGroupRepository(@NonNull Context context, @NonNull GroupId groupId) {
    this.context  = context;
    this.executor = SignalExecutors.BOUNDED;
    this.groupId  = groupId;
  }

  public GroupId getGroupId() {
    return groupId;
  }

  void getGroupState(@NonNull Consumer<GroupStateResult> onGroupStateLoaded) {
    executor.execute(() -> onGroupStateLoaded.accept(getGroupState()));
  }

  @WorkerThread
  private GroupStateResult getGroupState() {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Recipient      groupRecipient = Recipient.externalGroup(context, groupId);
    long           threadId       = threadDatabase.getThreadIdFor(groupRecipient);

    return new GroupStateResult(threadId, groupRecipient);
  }

  void setExpiration(int newExpirationTime, @NonNull Error error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.updateGroupTimer(context, groupId.requirePush(), newExpirationTime);
      } catch (GroupInsufficientRightsException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NOT_A_MEMBER);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      }
    });
  }

  void applyMembershipRightsChange(@NonNull GroupAccessControl newRights, @NonNull Error error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.applyMembershipAdditionRightsChange(context, groupId.requireV2(), newRights);
      } catch (GroupInsufficientRightsException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupChangeFailedException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      }
    });
  }

  void applyAttributesRightsChange(@NonNull GroupAccessControl newRights, @NonNull Error error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.applyAttributesRightsChange(context, groupId.requireV2(), newRights);
      } catch (GroupInsufficientRightsException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupChangeFailedException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      }
    });
  }

  public void getRecipient(@NonNull Consumer<Recipient> recipientCallback) {
    SimpleTask.run(SignalExecutors.BOUNDED,
                   () -> Recipient.externalGroup(context, groupId),
                   recipientCallback::accept);
  }

  public void setMuteUntil(long until) {
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientId recipientId = Recipient.externalGroup(context, groupId).getId();
      DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until);
    });
  }

  static final class GroupStateResult {

    private final long      threadId;
    private final Recipient recipient;

    private GroupStateResult(long threadId,
                             Recipient recipient)
    {
      this.threadId  = threadId;
      this.recipient = recipient;
    }

    long getThreadId() {
      return threadId;
    }

    Recipient getRecipient() {
      return recipient;
    }
  }

  public enum FailureReason {
    NO_RIGHTS(R.string.ManageGroupActivity_you_dont_have_the_rights_to_do_this),
    NOT_A_MEMBER(R.string.ManageGroupActivity_youre_not_a_member_of_the_group),
    OTHER(R.string.ManageGroupActivity_failed_to_update_the_group);

    private final @StringRes int toastMessage;

    FailureReason(@StringRes int toastMessage) {
      this.toastMessage = toastMessage;
    }

    public @StringRes int getToastMessage() {
      return toastMessage;
    }
  }

  public interface Error {
    void onError(@NonNull FailureReason failureReason);
  }
}
