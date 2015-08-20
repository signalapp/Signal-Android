package de.gdata.messaging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import de.gdata.messaging.util.GService;

/**
 * Created by jan on 17.08.15.
 */
public class AppUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri packageName = intent.getData();
        if(packageName.toString().contains("mobilesecurity")) {
            context.startService(new Intent(context, GService.class));
            GService.bindISFAService();
        }
    }
}

