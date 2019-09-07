package org.thoughtcrime.securesms.contacts.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FeatureFlags;

import java.io.IOException;

public class DirectoryHelper {

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
    if (FeatureFlags.UUIDS) {
      // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    } else {
      DirectoryHelperV1.refreshDirectory(context, notifyOfNewUsers);
    }
  }

  @WorkerThread
  public static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
    if (FeatureFlags.UUIDS) {
      // TODO [greyson] Create a DirectoryHelperV2 when appropriate.
      return DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
    } else {
      return DirectoryHelperV1.refreshDirectoryFor(context, recipient, notifyOfNewUsers);
    }
  }
}
