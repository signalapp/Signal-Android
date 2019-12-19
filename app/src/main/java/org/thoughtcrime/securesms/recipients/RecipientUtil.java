package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceBlockedUpdateJob;
import org.thoughtcrime.securesms.jobs.RotateProfileKeyJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;

public class RecipientUtil {

  private static final String TAG = Log.tag(RecipientUtil.class);

  /**
   * This method will do it's best to craft a fully-populated {@link SignalServiceAddress} based on
   * the provided recipient. This includes performing a possible network request if no UUID is
   * available.
   */
  @WorkerThread
  public static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) {
    recipient = recipient.resolve();

    if (!recipient.getUuid().isPresent() && !recipient.getE164().isPresent()) {
      throw new AssertionError(recipient.getId() + " - No UUID or phone number!");
    }

    if (FeatureFlags.uuids() && !recipient.getUuid().isPresent()) {
      Log.i(TAG, recipient.getId() + " is missing a UUID...");
      try {
        RegisteredState state = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
        recipient = Recipient.resolved(recipient.getId());
        Log.i(TAG, "Successfully performed a UUID fetch for " + recipient.getId() + ". Registered: " + state);
      } catch (IOException e) {
        Log.w(TAG, "Failed to fetch a UUID for " + recipient.getId() + ". Scheduling a future fetch and building an address without one.");
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
      }
    }

    return new SignalServiceAddress(Optional.fromNullable(recipient.getUuid().orNull()), Optional.fromNullable(recipient.resolve().getE164().orNull()));
  }

  public static boolean isBlockable(@NonNull Recipient recipient) {
    Recipient resolved = recipient.resolve();
    return resolved.isPushGroup() || resolved.hasServiceIdentifier();
  }

  @WorkerThread
  public static void block(@NonNull Context context, @NonNull Recipient recipient) {
    if (!isBlockable(recipient)) {
      throw new AssertionError("Recipient is not blockable!");
    }

    Recipient resolved = recipient.resolve();

    DatabaseFactory.getRecipientDatabase(context).setBlocked(resolved.getId(), true);

    if (resolved.isGroup() && DatabaseFactory.getGroupDatabase(context).isActive(resolved.requireGroupId())) {
      long                                threadId     = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(resolved);
      Optional<OutgoingGroupMediaMessage> leaveMessage = GroupUtil.createGroupLeaveMessage(context, resolved);

      if (threadId != -1 && leaveMessage.isPresent()) {
        MessageSender.send(context, leaveMessage.get(), threadId, false, null);

        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
        String        groupId       = resolved.requireGroupId();
        groupDatabase.setActive(groupId, false);
        groupDatabase.remove(groupId, Recipient.self().getId());
      } else {
        Log.w(TAG, "Failed to leave group. Can't block.");
        Toast.makeText(context, R.string.RecipientPreferenceActivity_error_leaving_group, Toast.LENGTH_LONG).show();
      }
    }

    if (resolved.isSystemContact() || resolved.isProfileSharing()) {
      ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
    }

    ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
  }

  @WorkerThread
  public static void unblock(@NonNull Context context, @NonNull Recipient recipient) {
    if (!isBlockable(recipient)) {
      throw new AssertionError("Recipient is not blockable!");
    }

    DatabaseFactory.getRecipientDatabase(context).setBlocked(recipient.getId(), false);
    ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
  }

  @WorkerThread
  public static boolean isRecipientMessageRequestAccepted(@NonNull Context context, @Nullable Recipient recipient) {
    if (recipient == null || !FeatureFlags.messageRequests()) return true;

    Recipient resolved = recipient.resolve();

    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    long           threadId       = threadDatabase.getThreadIdFor(resolved);
    boolean        hasSentMessage = threadDatabase.getLastSeenAndHasSent(threadId).second() == Boolean.TRUE;

    return hasSentMessage || resolved.isProfileSharing() || resolved.isSystemContact();
  }
}
