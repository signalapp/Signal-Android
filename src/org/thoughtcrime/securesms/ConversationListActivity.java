package org.thoughtcrime.securesms;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.ApplicationExportManager.ApplicationExportListener;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.MemoryCleaner;

public class ConversationListActivity extends SherlockFragmentActivity
    implements ConversationListFragment.ConversationSelectedListener
  {

  private ConversationListFragment fragment;
  private MasterSecret masterSecret;

  private BroadcastReceiver killActivityReceiver;
  private BroadcastReceiver newKeyReceiver;
  private ApplicationMigrationManager migrationManager;

  private boolean havePromptedForPassphrase = false;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setTitle("TextSecure");

    initializeKillReceiver();
    initializeSenderReceiverService();
    initializeResources();
    initializeContactUpdatesReceiver();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.w("SecureSMS", "Got onNewIntent...");
    createConversationIfNecessary(intent);
  }

  @Override
  public void onPause() {
    super.onPause();

    if (newKeyReceiver != null) {
      Log.w("ConversationListActivity", "Unregistering receiver...");
      unregisterReceiver(newKeyReceiver);
      newKeyReceiver = null;
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.w("ConversationListActivity", "onResume called...");

    clearNotifications();
    initializeKeyCachingServiceRegistration();
  }

  @Override
  public void onStart() {
    super.onStart();
    registerPassphraseActivityStarted();
  }

  @Override
  public void onStop() {
    super.onStop();
    havePromptedForPassphrase = false;
    registerPassphraseActivityStopped();
  }

  @Override
  public void onDestroy() {
    Log.w("SecureSMS", "onDestroy...");
    unregisterReceiver(killActivityReceiver);
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    Log.w("ConversationListActivity", "onPrepareOptionsMenu...");
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    if (this.masterSecret == null) inflater.inflate(R.menu.text_secure_locked, menu);
    else                           inflater.inflate(R.menu.text_secure_normal, menu);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_new_message:      createConversation(-1, null); return true;
    case R.id.menu_unlock:           promptForPassphrase();        return true;
    case R.id.menu_settings:         handleDisplaySettings();      return true;
    case R.id.menu_export:           handleExportDatabase();       return true;
    case R.id.menu_import:           handleImportDatabase();       return true;
    case R.id.menu_clear_passphrase: handleClearPassphrase();      return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients) {
    createConversation(threadId, recipients);
  }

  private void createConversation(long threadId, Recipients recipients) {
    if (this.masterSecret == null) {
      promptForPassphrase();
      return;
    }

    Log.w("ConversationListActivity", "Creating conversation: " + threadId);

    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra("recipients", recipients);
    intent.putExtra("thread_id", threadId);
    intent.putExtra("master_secret", masterSecret);
    startActivity(intent);
  }

  private void promptForPassphrase() {
    havePromptedForPassphrase = true;
    if (hasSelectedPassphrase()) startActivity(new Intent(this, PassphrasePromptActivity.class));
    else                         startActivity(new Intent(this, PassphraseCreateActivity.class));
  }

  private boolean hasSelectedPassphrase() {
    SharedPreferences settings = getSharedPreferences(KeyCachingService.PREFERENCES_NAME, 0);
    return settings.getBoolean("passphrase_initialized", false);
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    preferencesIntent.putExtra("master_secret", masterSecret);
    startActivity(preferencesIntent);
  }

  private void handleExportDatabase() {
    ApplicationExportManager exportManager = new ApplicationExportManager(this);
    exportManager.exportDatabase();
  }

  private void handleImportDatabase() {
    ApplicationExportManager exportManager = new ApplicationExportManager(this);
    ApplicationExportListener listener = new ApplicationExportManager.ApplicationExportListener() {
      @Override
      public void onPrepareForImport() {
        initializeWithMasterSecret(null);

        Intent clearKeyIntent = new Intent(KeyCachingService.CLEAR_KEY_ACTION, null,
                                           ConversationListActivity.this, KeyCachingService.class);
        startService(clearKeyIntent);
      }
    };

    exportManager.setListener(listener);
    exportManager.importDatabase();
  }

  private void handleClearPassphrase() {
    Intent keyService = new Intent(this, KeyCachingService.class);

    keyService.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(keyService);

    this.masterSecret = null;
    fragment.setMasterSecret(null);

    promptForPassphrase();
  }

  private void initializeWithMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    if (masterSecret != null) {
      if (!IdentityKeyUtil.hasIdentityKey(this)) {
        new Thread(new IdentityKeyInitializer()).start();
      }

      if (!MasterSecretUtil.hasAsymmericMasterSecret(this)) {
        new Thread(new AsymmetricMasteSecretInitializer()).start();
      }

      if (!isDatabaseMigrated()) initializeDatabaseMigration();
      else                       DecryptingQueue.schedulePendingDecrypts(this, masterSecret);
    }

    this.fragment.setMasterSecret(masterSecret);
    this.invalidateOptionsMenu();
    createConversationIfNecessary(this.getIntent());
  }

  private void initializeKillReceiver() {
    this.killActivityReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        finish();
      }
    };

    registerReceiver(this.killActivityReceiver,
                     new IntentFilter(KeyCachingService.PASSPHRASE_EXPIRED_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);
  }

  private void initializeContactUpdatesReceiver() {
    ContentObserver observer = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        RecipientFactory.clearCache();
      }
    };

    getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                                                 true, observer);
  }

  private void initializeSenderReceiverService() {
    Intent smsSenderIntent = new Intent(SendReceiveService.SEND_SMS_ACTION, null, this,
                                        SendReceiveService.class);
    Intent mmsSenderIntent = new Intent(SendReceiveService.SEND_MMS_ACTION, null, this,
                                        SendReceiveService.class);
    startService(smsSenderIntent);
    startService(mmsSenderIntent);
  }

  private void initializeDatabaseMigration() {
    if (migrationManager == null) {
      migrationManager = new ApplicationMigrationManager(this, masterSecret);

      ApplicationMigrationManager.ApplicationMigrationListener listener =
          new ApplicationMigrationManager.ApplicationMigrationListener() {
            @Override
            public void applicationMigrationComplete() {
              if (masterSecret != null)
                DecryptingQueue.schedulePendingDecrypts(ConversationListActivity.this,
                                                        masterSecret);
            }
          };

      migrationManager.setMigrationListener(listener);
      migrationManager.migrate();
    }
  }

  private void initializeKeyCachingServiceRegistration() {
    Log.w("ConversationListActivity", "Checking caching service...");
    this.newKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w("ConversationListActivity", "Got a key broadcast...");
        initializeWithMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);

    Intent bindIntent = new Intent(this, KeyCachingService.class);
    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    this.fragment = (ConversationListFragment)this.getSupportFragmentManager()
        .findFragmentById(R.id.fragment_content);
  }

  private boolean isDatabaseMigrated() {
    return this.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE)
            .getBoolean("migrated", false);
  }

  private void clearNotifications() {
    NotificationManager manager =
        (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

    manager.cancel(KeyCachingService.NOTIFICATION_ID);
  }

  private void createConversationIfNecessary(Intent intent) {
    Log.w("ConversationListActivity", "createConversationIfNecessary called");
    long thread           = intent.getLongExtra("thread_id", -1L);
    Recipients recipients = null;

    if (intent.getAction() != null && intent.getAction().equals("android.intent.action.SENDTO")) {
      Log.w("ConversationListActivity", "Intent has sendto action...");
      try {
        recipients = RecipientFactory.getRecipientsFromString(this, intent.getData().getSchemeSpecificPart(), false);
        thread     = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);
      } catch (RecipientFormattingException rfe) {
        recipients = null;
      }
    } else {
      recipients = intent.getParcelableExtra("recipients");
    }

    if (recipients != null) {
      Log.w("ConversationListActivity", "Creating conversation: " + thread + " , " + recipients);
      createConversation(thread, recipients);
      intent.putExtra("thread_id", -1L);
      intent.putExtra("recipients", (Parcelable)null);
      intent.setAction(null);
    }
  }

  private void registerPassphraseActivityStarted() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_START_EVENT);
    startService(intent);
  }

  private void registerPassphraseActivityStopped() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_STOP_EVENT);
    startService(intent);
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
      MasterSecret masterSecret            = keyCachingService.getMasterSecret();

      initializeWithMasterSecret(masterSecret);

      if (masterSecret == null && !havePromptedForPassphrase)
        promptForPassphrase();

      Intent cachingIntent = new Intent(ConversationListActivity.this, KeyCachingService.class);
      startService(cachingIntent);

      try {
        ConversationListActivity.this.unbindService(this);
      } catch (IllegalArgumentException iae) {
        Log.w("SecureSMS", iae);
      }
    }

    public void onServiceDisconnected(ComponentName name) {}
  };

  private class IdentityKeyInitializer implements Runnable {
    public void run() {
      IdentityKeyUtil.generateIdentityKeys(ConversationListActivity.this, masterSecret);
    }
  }

  private class AsymmetricMasteSecretInitializer implements Runnable {
    public void run() {
      MasterSecretUtil.generateAsymmetricMasterSecret(ConversationListActivity.this, masterSecret);
    }
  }

}
