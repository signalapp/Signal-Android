package org.thoughtcrime.securesms.util;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * We actually don't need to authenticate, but the Android framework needs this.
 * This was shamelessly taken from
 * https://developer.android.com/training/sync-adapters/creating-authenticator.html
 *
 * Created by Lukas Barth on 28.02.14.
 */
public class DirectorySyncAuthenticatorService extends Service {
    private DirectorySyncAuthenticator mAuthenticator;

    @Override
    public void onCreate() {
        mAuthenticator = new DirectorySyncAuthenticator(this);
    }

    /*
     * When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }

}
