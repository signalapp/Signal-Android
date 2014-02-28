package org.thoughtcrime.securesms.util;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by tinloaf on 28.02.14.
 */
public class DirectorySyncService extends Service {
    private static DirectorySyncAdapter adapter = null;
    private static final Object sAdapterLock = new Object();

    @Override
    public void onCreate() {
        synchronized (DirectorySyncService.sAdapterLock) {
            if (DirectorySyncService.adapter == null) {
                DirectorySyncService.adapter = new DirectorySyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return DirectorySyncService.adapter.getSyncAdapterBinder();
    }
}
