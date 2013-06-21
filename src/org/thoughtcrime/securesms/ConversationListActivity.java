package org.thoughtcrime.securesms;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.WindowManager;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.thoughtcrime.securesms.ApplicationExportManager.ApplicationExportListener;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;

public class ConversationListActivity extends PassphraseRequiredSherlockFragmentActivity
    implements ConversationListFragment.ConversationSelectedListener
  {
  private final DynamicTheme dynamicTheme = new DynamicTheme();

  private ConversationListFragment fragment;
  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    super.onCreate(icicle);

    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setTitle("TextSecure");

    initializeSenderReceiverService();
    initializeResources();
    initializeContactUpdatesReceiver();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onDestroy() {
    Log.w("ConversationListActivity", "onDestroy...");
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  @Override
  public void onMasterSecretCleared() {
//    this.fragment.setMasterSecret(null);
    startActivity(new Intent(this, RoutingActivity.class));
    super.onMasterSecretCleared();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int defaultType = ThreadDatabase.DistributionTypes.DEFAULT;

    switch (item.getItemId()) {
    case R.id.menu_new_message:      createConversation(-1, null, defaultType); return true;
    case R.id.menu_settings:         handleDisplaySettings();                   return true;
    case R.id.menu_export:           handleExportDatabase();                    return true;
    case R.id.menu_import:           handleImportDatabase();                    return true;
    case R.id.menu_clear_passphrase: handleClearPassphrase();                   return true;
    case R.id.menu_mark_all_read:    handleMarkAllRead();                       return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    createConversation(threadId, recipients, distributionType);
  }

  private void createConversation(long threadId, Recipients recipients, int distributionType) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    startActivity(intent);
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
        onMasterSecretCleared();
        handleClearPassphrase();
      }
    };

    exportManager.setListener(listener);
    exportManager.importDatabase();
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
        MessageNotifier.updateNotification(ConversationListActivity.this, masterSecret);
        return null;
      }
    }.execute();
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

  private void initializeResources() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                           WindowManager.LayoutParams.FLAG_SECURE);
    }

    this.masterSecret = (MasterSecret)getIntent().getParcelableExtra("master_secret");

    this.fragment = (ConversationListFragment)this.getSupportFragmentManager()
        .findFragmentById(R.id.fragment_content);

    this.fragment.setMasterSecret(masterSecret);
  }
}
