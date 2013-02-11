package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.WindowManager;

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

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class ConversationListActivity extends PassphraseRequiredSherlockFragmentActivity
    implements ConversationListFragment.ConversationSelectedListener
  {

  private ConversationListFragment fragment;
  private MasterSecret masterSecret;

  private ApplicationMigrationManager migrationManager;

  private boolean havePromptedForPassphrase = false;
  private boolean isVisible                 = false;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.conversation_list_activity);
    getSupportActionBar().setTitle("TextSecure");

    initializeSenderReceiverService();
    initializeResources();
    initializeContactUpdatesReceiver();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    this.setIntent(intent);
  }

  @Override
  public void onResume() {
    super.onResume();

    isVisible = true;
  }

  @Override
  public void onPause() {
    super.onPause();

    isVisible = false;
  }

  @Override
  public void onStop() {
    super.onStop();
    havePromptedForPassphrase = false;
  }

  @Override
  public void onDestroy() {
    Log.w("ConversationListActivity", "onDestroy...");
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  @Override
  public void onMasterSecretCleared() {
    this.masterSecret = null;
    this.fragment.setMasterSecret(null);
    this.invalidateOptionsMenu();

    if (!havePromptedForPassphrase && isVisible) {
      promptForPassphrase();
    }
  }

  @Override
  public void onNewMasterSecret(MasterSecret masterSecret) {
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
    this.havePromptedForPassphrase = false;
    createConversationIfNecessary(this.getIntent());
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
    case R.id.menu_new_message:      createConversation(-1, null, null, null, null); return true;
    case R.id.menu_unlock:           promptForPassphrase();                          return true;
    case R.id.menu_settings:         handleDisplaySettings();                        return true;
    case R.id.menu_export:           handleExportDatabase();                         return true;
    case R.id.menu_import:           handleImportDatabase();                         return true;
    case R.id.menu_clear_passphrase: handleClearPassphrase();                        return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients) {
    createConversation(threadId, recipients, null, null, null);
  }

  private void createConversation(long threadId, Recipients recipients,
                                  String text, Uri imageUri, Uri audioUri)
  {
    if (this.masterSecret == null) {
      promptForPassphrase();
      return;
    }

    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(ConversationActivity.DRAFT_TEXT_EXTRA, text);
    intent.putExtra(ConversationActivity.DRAFT_IMAGE_EXTRA, imageUri);
    intent.putExtra(ConversationActivity.DRAFT_AUDIO_EXTRA, audioUri);

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

  private void initializeResources() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                           WindowManager.LayoutParams.FLAG_SECURE);
    }

    this.fragment = (ConversationListFragment)this.getSupportFragmentManager()
        .findFragmentById(R.id.fragment_content);
  }

  private boolean isDatabaseMigrated() {
    return this.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE)
            .getBoolean("migrated", false);
  }

  private void createConversationIfNecessary(Intent intent) {
    long thread           = intent.getLongExtra("thread_id", -1L);
    String type           = intent.getType();
    Recipients recipients = null;
    String draftText      = null;
    Uri draftImage        = null;
    Uri draftAudio        = null;

    if (Intent.ACTION_SENDTO.equals(intent.getAction())) {
      try {
        recipients = RecipientFactory.getRecipientsFromString(this, intent.getData().getSchemeSpecificPart(), false);
        thread     = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);
      } catch (RecipientFormattingException rfe) {
        recipients = null;
      }
    } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
      if ("text/plain".equals(type)) {
        draftText = intent.getStringExtra(Intent.EXTRA_TEXT);
      } else if (type.startsWith("image/")) {
        draftImage = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      } else if (type.startsWith("audio/")) {
        draftAudio = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      }
    } else {
      recipients = intent.getParcelableExtra("recipients");
    }

    if (recipients != null || Intent.ACTION_SEND.equals(intent.getAction())) {
      createConversation(thread, recipients, draftText, draftImage, draftAudio);

      intent.putExtra("thread_id", -1L);
      intent.putExtra("recipients", (Parcelable)null);
      intent.putExtra(Intent.EXTRA_TEXT, (String)null);
      intent.putExtra(Intent.EXTRA_STREAM, (Parcelable)null);
      intent.setAction(null);
    }
  }

  private class IdentityKeyInitializer implements Runnable {
    @Override
    public void run() {
      IdentityKeyUtil.generateIdentityKeys(ConversationListActivity.this, masterSecret);
    }
  }

  private class AsymmetricMasteSecretInitializer implements Runnable {
    @Override
    public void run() {
      MasterSecretUtil.generateAsymmetricMasterSecret(ConversationListActivity.this, masterSecret);
    }
  }
}
