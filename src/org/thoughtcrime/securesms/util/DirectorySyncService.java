package org.thoughtcrime.securesms.util;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * The service to run the directory sync in.
 * 
 * @author Lukas Barth
 */
public class DirectorySyncService extends Service {
    private static DirectorySyncAdapter adapter = null;
    private static final Object sAdapterLock = new Object();

    @Override
    public void onCreate() {
        synchronized (DirectorySyncService.sAdapterLock) {
            if (DirectorySyncService.adapter == null) {
                DirectorySyncService.adapter = DirectorySyncAdapter.getInstance(getApplicationContext());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return DirectorySyncService.adapter.getSyncAdapterBinder();
    }
}
