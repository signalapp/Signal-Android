package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class DirectoryRefreshJob extends ContextJob {

  private static final String TAG = DirectoryRefreshJob.class.getSimpleName();

  private static final String KEY_ADDRESS             = "address";
  private static final String KEY_NOTIFY_OF_NEW_USERS = "notify_of_new_users";

  @Nullable private transient Recipient    recipient;
            private transient boolean      notifyOfNewUsers;

  public DirectoryRefreshJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public DirectoryRefreshJob(@NonNull Context context, boolean notifyOfNewUsers) {
    this(context, null, notifyOfNewUsers);
  }

  public DirectoryRefreshJob(@NonNull Context context,
                             @Nullable Recipient recipient,
                                       boolean notifyOfNewUsers)
  {
    super(context, JobParameters.newBuilder()
                                .withGroupId(DirectoryRefreshJob.class.getSimpleName())
                                .withNetworkRequirement()
                                .create());

    this.recipient        = recipient;
    this.notifyOfNewUsers = notifyOfNewUsers;
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    String  serializedAddress = data.getString(KEY_ADDRESS);
    Address address           = serializedAddress != null ? Address.fromSerialized(serializedAddress) : null;

    recipient        = address != null ? Recipient.from(context, address, true) : null;
    notifyOfNewUsers = data.getBoolean(KEY_NOTIFY_OF_NEW_USERS);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putString(KEY_ADDRESS, recipient != null ? recipient.getAddress().serialize() : null)
                      .putBoolean(KEY_NOTIFY_OF_NEW_USERS, notifyOfNewUsers)
                      .build();
  }

  @Override
  public void onRun() throws IOException {
    Log.i(TAG, "DirectoryRefreshJob.onRun()");

    if (recipient == null) {
      DirectoryHelper.refreshDirectory(context, notifyOfNewUsers);
    } else {
      DirectoryHelper.refreshDirectoryFor(context, recipient);
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {}
}
