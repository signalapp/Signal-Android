package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends BaseJob {

  public static final String KEY = "DirectoryRefreshJob";

  private static final String TAG = Log.tag(DirectoryRefreshJob.class);

  private static final String KEY_RECIPIENT           = "recipient";
  private static final String KEY_NOTIFY_OF_NEW_USERS = "notify_of_new_users";

  @Nullable private Recipient recipient;
            private boolean   notifyOfNewUsers;

  public DirectoryRefreshJob(boolean notifyOfNewUsers) {
    this(null, notifyOfNewUsers);
  }

  public DirectoryRefreshJob(@Nullable Recipient recipient,
                             boolean notifyOfNewUsers)
  {
    this(new Job.Parameters.Builder()
                           .setQueue(StorageSyncJob.QUEUE_KEY)
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build(),
         recipient,
         notifyOfNewUsers);
  }

  private DirectoryRefreshJob(@NonNull Job.Parameters parameters, @Nullable Recipient recipient, boolean notifyOfNewUsers) {
    super(parameters);

    this.recipient        = recipient;
    this.notifyOfNewUsers = notifyOfNewUsers;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENT, recipient != null ? recipient.getId().serialize() : null)
                                    .putBoolean(KEY_NOTIFY_OF_NEW_USERS, notifyOfNewUsers)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected boolean shouldTrace() {
    return true;
  }

  @Override
  public void onRun() throws IOException {
    Log.i(TAG, "DirectoryRefreshJob.onRun()");

    if (recipient == null) {
      ContactDiscovery.refreshAll(context, notifyOfNewUsers);
    } else {
      ContactDiscovery.refresh(context, recipient, notifyOfNewUsers);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {}

  public static final class Factory implements Job.Factory<DirectoryRefreshJob> {

    @Override
    public @NonNull DirectoryRefreshJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      String    serialized       = data.hasString(KEY_RECIPIENT) ? data.getString(KEY_RECIPIENT) : null;
      Recipient recipient        = serialized != null ? Recipient.resolved(RecipientId.from(serialized)) : null;
      boolean   notifyOfNewUsers = data.getBoolean(KEY_NOTIFY_OF_NEW_USERS);

      return new DirectoryRefreshJob(parameters, recipient, notifyOfNewUsers);
    }
  }
}
