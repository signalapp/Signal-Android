package org.thoughtcrime.securesms.contacts.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FeatureFlags;

import java.io.IOException;

public class DirectoryHelper {

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
    if (FeatureFlags.uuids()) {
      // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    } else {
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    }

    if (FeatureFlags.storageService()) {
      ApplicationDependencies.getJobManager().add(new StorageSyncJob());
    }
  }

  @WorkerThread
  public static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
    RegisteredState originalRegisteredState = recipient.resolve().getRegistered();
    RegisteredState newRegisteredState      = null;

    if (FeatureFlags.uuids()) {
      // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
      newRegisteredState = DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
    } else {
      newRegisteredState = DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
    }

    if (FeatureFlags.storageService() && newRegisteredState != originalRegisteredState) {
      ApplicationDependencies.getJobManager().add(new StorageSyncJob());
    }

    return newRegisteredState;
  }
}
