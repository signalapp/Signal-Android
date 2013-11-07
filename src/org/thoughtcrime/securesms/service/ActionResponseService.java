package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.PromptMmsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.KeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.InvalidMessageException;

import java.util.Iterator;

import ws.com.google.android.mms.MmsException;

/**
 * Service which allows user to respond-via-message during an incoming call
 *
 * @author Matthew Gill
 */
public class ActionResponseService extends Service {

    public static String RESPOND_VIA_MESSAGE_ACTION = "android.intent.action.RESPOND_VIA_MESSAGE";

    private MasterSecret masterSecret;
    private boolean      hasSecret;
    private Object       masterSecretLock = new Object();

    private NewKeyReceiver newKeyReceiver;
    private ClearKeyReceiver clearKeyReceiver;

    private boolean isMmsEnabled = true;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        initializeMasterSecret();
        // I cannot call this due to no android.permission.WRITE_APN_SETTINGS permission, how does ConversationActivity get away with it?
        //initializeMmsEnabledCheck();
    }

    @Override
    public void onDestroy() {
        Log.w("ActionResponseService", "onDestroy()...");
        super.onDestroy();

        if (newKeyReceiver != null)
            unregisterReceiver(newKeyReceiver);

        if (clearKeyReceiver != null)
            unregisterReceiver(clearKeyReceiver);
    }

    private void initializeMasterSecret() {
        hasSecret           = false;
        newKeyReceiver      = new NewKeyReceiver();
        clearKeyReceiver    = new ClearKeyReceiver();

        IntentFilter newKeyFilter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
        registerReceiver(newKeyReceiver, newKeyFilter, KeyCachingService.KEY_PERMISSION, null);

        IntentFilter clearKeyFilter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);
        registerReceiver(clearKeyReceiver, clearKeyFilter, KeyCachingService.KEY_PERMISSION, null);

        Intent bindIntent   = new Intent(this, KeyCachingService.class);
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initializeMmsEnabledCheck() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return MmsSendHelper.hasNecessaryApnDetails(ActionResponseService.this);
            }

            @Override
            protected void onPostExecute(Boolean isMmsEnabled) {
                ActionResponseService.this.isMmsEnabled = isMmsEnabled;
            }
        }.execute();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Recipients recipients = getRecipients(intent);

            if(recipients != null)
            {
                long       threadId   = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);

                String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                String body = intent.getStringExtra(Intent.EXTRA_TEXT);

                boolean isEncryptedConversation = isEncryptedConversation(recipients);

                if ((!recipients.isSingleRecipient() || recipients.isEmailRecipient()) && !isMmsEnabled) {
                    handleManualMmsRequired(intent);
                } else if (recipients.isEmailRecipient() || !recipients.isSingleRecipient()) {
                    MessageSender.sendMms(ActionResponseService.this, masterSecret, recipients,
                        threadId, new SlideDeck(), body, ThreadDatabase.DistributionTypes.DEFAULT, isEncryptedConversation);
                } else {
                    OutgoingTextMessage message;

                    if (isEncryptedConversation) {
                        message = new OutgoingEncryptedMessage(recipients, body);
                    } else {
                        message = new OutgoingTextMessage(recipients, body);
                    }

                    Log.w("ConversationActivity", "Sending message...");
                    MessageSender.send(ActionResponseService.this, masterSecret, message, threadId);
                }
            }
        } catch (MmsException e) {
            Log.w("ActionResponseService", e);
        }

        return START_STICKY;
    }

    private void handleManualMmsRequired(Intent in) {
        Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, PromptMmsActivity.class);
        intent.putExtras(in.getExtras());
        startActivity(intent);
    }

    private Recipients getRecipients(Intent intent)
    {
        Recipients recipients = null;
        try {
            String data = intent.getData().getSchemeSpecificPart();
            recipients = RecipientFactory.getRecipientsFromString(this, data, false);
        } catch (Exception e) {
            recipients = null;
        }
        return recipients;
    }

    /**
     * @param recipient
     * @return true if there is a single recipient else false
     */
    private boolean isSingleConversation(Recipients recipient) {
        return recipient != null && recipient.isSingleRecipient();
    }

    /**
     * @param recipient
     * @return true if the conversation is encrypted else false
     */
    private boolean isEncryptedConversation(Recipients recipient)
    {
        return (isSingleConversation(recipient) && KeyUtil.isSessionFor(this, recipient.getPrimaryRecipient()));
    }

    private void setMasterSecret(MasterSecret masterSecret) {
        synchronized (masterSecretLock) {
            this.masterSecret = masterSecret;
            this.hasSecret    = true;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
            MasterSecret masterSecret            = keyCachingService.getMasterSecret();

            ActionResponseService.this.setMasterSecret(masterSecret);

            ActionResponseService.this.unbindService(this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    /**
     * This class receives broadcast notifications containing the MasterSecret
     */
    private class NewKeyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w("ActionResponseService", "Got a MasterSecret broadcast...");
            ActionResponseService.this.setMasterSecret((MasterSecret) intent.getParcelableExtra("master_secret"));
        }
    }

    /**
     * This class receives broadcast notifications to clear the MasterSecret.
     */
    private class ClearKeyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w("ActionResponseService", "Got a clear mastersecret broadcast...");
            ActionResponseService.this.setMasterSecret(null);
        }
    };
}
