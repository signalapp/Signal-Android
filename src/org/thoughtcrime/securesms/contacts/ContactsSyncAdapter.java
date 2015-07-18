package org.thoughtcrime.securesms.contacts;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import org.thoughtcrime.securesms.util.DirectoryHelper;

import java.io.IOException;

public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = ContactsSyncAdapter.class.getSimpleName();

  public ContactsSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult)
  {
    try {
      DirectoryHelper.refreshDirectory(getContext());
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

}
