package org.thoughtcrime.securesms.util;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

/**
 * Created by Lukas Barth on 28.02.14.
 */
public class DirectorySyncAdapter extends AbstractThreadedSyncAdapter {
    private Context ctx;

    public DirectorySyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);

        this.ctx = context;
    }

    public DirectorySyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize); // No parallel stuff. Min. API level is 9

        this.ctx = context;
    }

     public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {

        DirectoryHelper.refreshDirectory(this.ctx);
     }
}
