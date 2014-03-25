package org.thoughtcrime.securesms.util;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

/**
 * A SyncAdapter in charge of refreshing the directory of push-enabled contacts
 *
 * @author Lukas Barth
 */
public class DirectorySyncAdapter extends AbstractThreadedSyncAdapter {
  private Context context;

  private static DirectorySyncAdapter instance = null;

  private DirectorySyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);

    this.context = context;
  }

  private DirectorySyncAdapter(
          Context context,
          boolean autoInitialize,
          boolean allowParallelSyncs) {
    super(context, autoInitialize);

    this.context = context;
  }

  public static DirectorySyncAdapter getInstance(Context context) {
    if (DirectorySyncAdapter.instance == null) {
      DirectorySyncAdapter.instance = new DirectorySyncAdapter(context, true);
    }

    return DirectorySyncAdapter.instance;
  }

  public void onPerformSync(
          Account account,
          Bundle extras,
          String authority,
          ContentProviderClient provider,
          SyncResult syncResult) {

    DirectoryHelper.refreshDirectory(this.context);
  }
}
