package org.thoughtcrime.securesms.groups.ui.chooseadmin;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.IOException;
import java.util.List;

public final class ChooseNewAdminRepository {
  private Application context;

  ChooseNewAdminRepository(@NonNull Application context) {
    this.context = context;
  }

  @WorkerThread
  @NonNull UpdateResult updateAdminsAndLeave(@NonNull GroupId.V2 groupId, @NonNull List<RecipientId> newAdminIds) {
    try {
      GroupManager.addMemberAdminsAndLeaveGroup(context, groupId, newAdminIds);
      return new UpdateResult();
    } catch (GroupInsufficientRightsException e) {
      return new UpdateResult(GroupChangeFailureReason.NO_RIGHTS);
    } catch (GroupNotAMemberException e) {
      return new UpdateResult(GroupChangeFailureReason.NOT_A_MEMBER);
    } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
      return new UpdateResult(GroupChangeFailureReason.OTHER);
    }
  }

  static final class UpdateResult {
    final @Nullable GroupChangeFailureReason failureReason;

    UpdateResult() {
      this(null);
    }

    UpdateResult(@Nullable GroupChangeFailureReason failureReason) {
      this.failureReason = failureReason;
    }

    boolean isSuccess() {
      return failureReason == null;
    }

    @Nullable GroupChangeFailureReason getFailureReason() {
      return failureReason;
    }
  }
}
