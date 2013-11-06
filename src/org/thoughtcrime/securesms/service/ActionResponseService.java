package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Service which does nothing to qualify for default SMS app in KitKat+
 *
 * @author Matthew Gill
 */
public class ActionResponseService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
