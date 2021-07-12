package com.batsignal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Because IPC across User Profiles is limited to activities, this invisible Activity is launched by
 * BatApps to pass the phone numbers for the threads that should be hidden when the profile is
 * deactivated.
 */
public class BatAppsSyncActivity extends Activity {

    private static final String TAG = BatAppsSyncActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent().getStringExtra(Constants.CUSTOM_ACTION);
        if(Constants.ACTION_SAVE_CONTACTS.equals(action)) {

            String[] contacts = getIntent().getStringArrayExtra(Constants.EXTRA_CONTACTS_LIST);
            saveContacts(contacts);

        } else if(Constants.ACTION_REFRESH_CONVERSATIONS.equals(action)) {

            sendRefreshNotification(null);
        }
        finish();
    }

    public void saveContacts(String[] contacts) {

        Set<String> set = new HashSet<>(Arrays.asList(contacts));

        SharedPreferences preferences =
                this.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putStringSet(Constants.EXTRA_CONTACTS_LIST, set);
        editor.commit();
        editor.apply();

        BatAppsContactsManager.getInstance().updateAnonymousContacts(set);

        sendRefreshNotification(contacts);
    }

    public void sendRefreshNotification(String[] contacts) {

        Intent intent = new Intent(Constants.ACTION_CONTACTS_RECEIVED);
        if(contacts != null)
            intent.putExtra(Constants.EXTRA_CONTACTS_LIST, contacts);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
