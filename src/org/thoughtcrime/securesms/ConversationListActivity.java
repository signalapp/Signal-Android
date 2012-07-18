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
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.ApplicationExportManager.ApplicationExportListener;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
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

    Intent intent = new Intent(this, ComposeMessageActivity.class);
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

        DatabaseFactory.getInstance(ConversationListActivity.this).close();
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

    getContentResolver().registerContentObserver(ContactAccessor.getInstance().getContactsUri(),
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
    ApplicationMigrationManager migrationManager = new ApplicationMigrationManager(this,
                                                                                   masterSecret);
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
    long thread           = intent.getLongExtra("thread_id", -1L);
    Recipients recipients = null;

    if (intent.getAction() != null && intent.getAction().equals("android.intent.action.SENDTO")) {
      try {
        recipients = RecipientFactory.getRecipientsFromString(this, intent.getData().getSchemeSpecificPart());
        thread     = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);
      } catch (RecipientFormattingException rfe) {
        recipients = null;
      }
    } else {
      recipients = intent.getParcelableExtra("recipients");
    }

    if (recipients != null) {
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



//  @Override
//  public boolean onPrepareOptionsMenu(Menu menu) {
//    super.onPrepareOptionsMenu(menu);
//
//    menu.clear();
//
//    if (!batchMode) prepareNormalMenu(menu);
//    else            prepareBatchModeMenu(menu);
//
//    return true;
//  }
//
//  private void prepareNormalMenu(Menu menu) {
//    menu.add(0, MENU_BATCH_MODE, Menu.NONE, "Batch Mode")
//      .setIcon(android.R.drawable.ic_menu_share);
//
//    if (masterSecret != null) {
//      menu.add(0, MENU_SEND_KEY, Menu.NONE, "Secure Session")
//        .setIcon(R.drawable.ic_lock_message_sms);
//    } else {
//      menu.add(0, MENU_PASSPHRASE_KEY, Menu.NONE, "Enter passphrase")
//        .setIcon(R.drawable.ic_lock_message_sms);
//    }
//
//    menu.add(0, MENU_NEW_MESSAGE, Menu.NONE, "New Message")
//      .setIcon(R.drawable.ic_menu_msg_compose_holo_dark)
//      .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//      android.widget.SearchView searchView =
//          new android.widget.SearchView(this.getApplicationContext());
//
//      menu.add(0, MENU_SEARCH, Menu.NONE, "Search")
//        .setActionView(searchView)
//        .setIcon(R.drawable.ic_menu_search_holo_dark)
//        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
//                         MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
//
////      initializeSearch(searchView);
//    } else {
//      ImageView image = new ImageView(this);
//      image.setImageResource(R.drawable.ic_menu_search_holo_dark);
//      image.setVisibility(View.INVISIBLE);
//      FrameLayout searchView = new FrameLayout(this);
//      searchView.addView(image);
//
//      menu.add(0, -1, Menu.NONE, "")
//      .setActionView(searchView)
//      .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//    }
//
//    menu.add(0, MENU_PREFERENCES_KEY, Menu.NONE, "Settings")
//      .setIcon(android.R.drawable.ic_menu_preferences);
//
//    SubMenu importExportMenu = menu.addSubMenu("Import/Export")
//      .setIcon(android.R.drawable.ic_menu_save);
//    importExportMenu.add(0, MENU_EXPORT, Menu.NONE, "Export To SD Card")
//      .setIcon(android.R.drawable.ic_menu_save);
//    importExportMenu.add(0, MENU_IMPORT, Menu.NONE, "Import From SD Card")
//      .setIcon(android.R.drawable.ic_menu_revert);
//
//    if (masterSecret != null) {
//      menu.add(0, MENU_CLEAR_PASSPHRASE, Menu.NONE, "Clear Passphrase")
//      .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
//    }
//  }
//
//  private void prepareBatchModeMenu(Menu menu) {
//    menu.add(0, MENU_EXIT_BATCH, Menu.NONE, "Normal Mode")
//      .setIcon(android.R.drawable.ic_menu_set_as);
//
//    menu.add(0, MENU_DELETE_SELECTED_THREADS, Menu.NONE, "Delete Selected")
//      .setIcon(R.drawable.ic_menu_trash_holo_dark)
//      .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//
//    menu.add(0, MENU_SELECT_ALL_THREADS, Menu.NONE, "Select All")
//      .setIcon(android.R.drawable.ic_menu_add);
//
//    menu.add(0, MENU_CLEAR_SELECTION, Menu.NONE, "Unselect All")
//      .setIcon(android.R.drawable.ic_menu_revert);
//  }

//  @Override
//  public boolean onOptionsItemSelected(MenuItem item) {
//    super.onOptionsItemSelected(item);
//
//    switch (item.getItemId()) {
//    case MENU_NEW_MESSAGE:
//        createConversation(-1, null);
//        return true;
//    case MENU_SEND_KEY:
//      Intent intent = new Intent(this, SendKeyActivity.class);
//      intent.putExtra("master_secret", masterSecret);
//
//      startActivity(intent);
//      return true;
//    case MENU_PASSPHRASE_KEY:
//      promptForPassphrase();
//      return true;
////    case MENU_BATCH_MODE:
////      initiateBatchMode();
////      return true;
//    case MENU_PREFERENCES_KEY:
//      Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
//      preferencesIntent.putExtra("master_secret", masterSecret);
//      startActivity(preferencesIntent);
//      return true;
//    case MENU_EXPORT:
//      ExportHandler exportHandler = new ExportHandler();
//      exportHandler.export();
//      return true;
//    case MENU_IMPORT:
//      ExportHandler importHandler = new ExportHandler();
//      importHandler.importFromSd();
//      return true;
////    case MENU_DELETE_SELECTED_THREADS: deleteSelectedThreads(); return true;
////    case MENU_SELECT_ALL_THREADS:      selectAllThreads();      return true;
////    case MENU_CLEAR_SELECTION:         unselectAllThreads();    return true;
////    case MENU_EXIT_BATCH:              stopBatchMode();         return true;
////    case MENU_CLEAR_PASSPHRASE:
////      Intent keyService = new Intent(this, KeyCachingService.class);
////      keyService.setAction(KeyCachingService.CLEAR_KEY_ACTION);
////      startService(keyService);
////      addConversationItems();
////      promptForPassphrase();
////      //      finish();
////      return true;
//    }
//
//    return false;
//  }



//  public void eulaComplete() {
//    clearNotifications();
//    initializeReceivers();
//    checkCachingService();
//  }

//  @Override
//  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
//    if (((AdapterView.AdapterContextMenuInfo)menuInfo).position > 0) {
//      Cursor cursor         = ((CursorAdapter)this.getListAdapter()).getCursor();
//      String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
//      Recipients recipients = RecipientFactory.getRecipientsForIds(this, recipientId);
//
//      menu.add(0, VIEW_THREAD_ID, Menu.NONE, "View thread");
//
//      if (recipients.isSingleRecipient()) {
//        if (recipients.getPrimaryRecipient().getName() != null) {
//          menu.add(0, VIEW_CONTACT_ID, Menu.NONE, "View contact");
//        } else {
//          menu.add(0, ADD_CONTACT_ID, Menu.NONE, "Add to contacts");
//        }
//      }
//
//      menu.add(0, DELETE_THREAD_ID, Menu.NONE, "Delete thread");
//    }
//  }

//  @Override
//  public boolean onContextItemSelected(android.view.MenuItem item) {
//    Cursor cursor         = ((CursorAdapter)this.getListAdapter()).getCursor();
//    long threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
//    String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
//    Recipients recipients = RecipientFactory.getRecipientsForIds(this, recipientId);
//
//    switch(item.getItemId()) {
//    case VIEW_THREAD_ID:
//      createConversation(threadId, recipients);
//      return true;
//    case VIEW_CONTACT_ID:
//      viewContact(recipients.getPrimaryRecipient());
//      return true;
//    case ADD_CONTACT_ID:
//      addContact(recipients.getPrimaryRecipient());
//      return true;
//    case DELETE_THREAD_ID:
//      deleteThread(threadId);
//      return true;
//    }
//    return false;
//  }

//  private void initiateBatchMode() {
//    this.batchMode = true;
//    ((ConversationListAdapter)this.getListAdapter()).initializeBatchMode(batchMode);
//  }
//
//  private void stopBatchMode() {
//    this.batchMode = false;
//    ((ConversationListAdapter)this.getListAdapter()).initializeBatchMode(batchMode);
//  }
//
//  private void selectAllThreads() {
//    ((ConversationListAdapter)this.getListAdapter()).selectAllThreads();
//  }
//
//  private void unselectAllThreads() {
//    ((ConversationListAdapter)this.getListAdapter()).unselectAllThreads();
//  }

//  private void deleteSelectedThreads() {
//    AlertDialog.Builder alert = new AlertDialog.Builder(this);
//    alert.setIcon(android.R.drawable.ic_dialog_alert);
//    alert.setTitle("Delete threads?");
//    alert.setMessage("Are you sure you wish to delete ALL selected conversation threads?");
//    alert.setCancelable(true);
//    alert.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
//      public void onClick(DialogInterface dialog, int which) {
//        Set<Long> selectedConversations = ((ConversationListAdapter)getListAdapter()).getBatchSelections();
//
//        if (!selectedConversations.isEmpty())
//          DatabaseFactory.getThreadDatabase(SecureSMS.this).deleteConversations(selectedConversations);
//      }
//    });
//    alert.setNegativeButton("Cancel", null);
//    alert.show();
//  }

//  private void registerForContactsUpdates() {
//    Log.w("SecureSMS", "Registering for contacts update...");
//    getContentResolver().registerContentObserver(ContactAccessor.getInstance().getContactsUri(), true, new ContactUpdateObserver());
//  }



//  private void addContact(Recipient recipient) {
//    RecipientFactory.getRecipientProvider().addContact(this, recipient);
//  }
//
//  private void viewContact(Recipient recipient) {
//    if (recipient.getPersonId() > 0) {
//      RecipientFactory.getRecipientProvider().viewContact(this, recipient);
//    }
//  }




//  private void initializeWithMasterSecret(MasterSecret masterSecret) {
//    this.masterSecret = masterSecret;
//
//    if (masterSecret != null) {
//      if (!IdentityKeyUtil.hasIdentityKey(this))            initializeIdentityKeys();
//      if (!MasterSecretUtil.hasAsymmericMasterSecret(this)) initializeAsymmetricMasterSecret();
//      if (!isDatabaseMigrated())                            initializeDatabaseMigration();
//      else                                                  DecryptingQueue.schedulePendingDecrypts(this, masterSecret);
//    }
//
//    this.fragment.setMasterSecret(masterSecret);
//    createConversationIfNecessary(this.getIntent());
//  }
//
//  private void initializeAsymmetricMasterSecret() {
//    new Thread(new AsymmetricMasteSecretInitializer()).start();
//  }
//
//  private void initializeIdentityKeys() {
//    new Thread(new IdentityKeyInitializer()).start();
//  }

//  private void initializeSearch(android.widget.SearchView searchView) {
//    searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
//      @Override
//      public boolean onQueryTextSubmit(String query) {
//        Cursor cursor = null;
//
//        if (query.length() > 0) {
//          List<String> numbers = ContactAccessor.getInstance().getNumbersForThreadSearchFilter(query, getContentResolver());
//          cursor  = DatabaseFactory.getThreadDatabase(SecureSMS.this).getFilteredConversationList(numbers);
//        } else {
//          cursor = DatabaseFactory.getThreadDatabase(SecureSMS.this).getConversationList();
//        }
//
//        if (cursor != null)
//          startManagingCursor(cursor);
//
//        if (getListAdapter() != null)
//          ((CursorAdapter)getListAdapter()).changeCursor(cursor);
//
//        return true;
//      }
//
//      @Override
//      public boolean onQueryTextChange(String newText) {
//        return onQueryTextSubmit(newText);
//      }
//    });
//  }

//  private void initializeReceivers() {
//    this.newKeyReceiver = new BroadcastReceiver() {
//      @Override
//      public void onReceive(Context context, Intent intent) {
//        Log.w("ConversationListActivity", "Got a key broadcast...");
//        initializeWithMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
//      }
//    };
//
//    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
//    registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
//  }

//  @Override
//  protected void onListItemClick(ListView l, View v, int position, long id) {
//    if (v instanceof ConversationHeaderView) {
//      ConversationHeaderView headerView = (ConversationHeaderView) v;
//      createConversation(headerView.getThreadId(), headerView.getRecipients());
//    }
//  }

//  private void createConversationIfNecessary(Intent intent) {
//    long thread           = intent.getLongExtra("thread_id", -1L);
//    Recipients recipients = null;
//
//    if (intent.getAction() != null && intent.getAction().equals("android.intent.action.SENDTO")) {
//      try {
//        recipients = RecipientFactory.getRecipientsFromString(this, intent.getData().getSchemeSpecificPart());
//        thread     = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);
//      } catch (RecipientFormattingException rfe) {
//        recipients = null;
//      }
//    } else {
//      recipients = intent.getParcelableExtra("recipients");
//    }
//
//    if (recipients != null) {
//      createConversation(thread, recipients);
//      intent.putExtra("thread_id", -1L);
//      intent.putExtra("recipients", (Parcelable)null);
//      intent.setAction(null);
//    }
//  }

//  private void createConversation(long threadId, Recipients recipients) {
//    if (this.masterSecret == null) {
//      promptForPassphrase();
//      return;
//    }
//
//    Intent intent = new Intent(this, ComposeMessageActivity.class);
//    intent.putExtra("recipients", recipients);
//    intent.putExtra("thread_id", threadId);
//    intent.putExtra("master_secret", masterSecret);
//    startActivity(intent);
//  }

//  private void addConversationItems() {
////    Cursor cursor = DatabaseFactory.getThreadDatabase(this).getConversationList();
////    startManagingCursor(cursor);
//    this.getLoaderManager();
////
//    if (masterSecret == null) setListAdapter(new ConversationListAdapter(this, null));
//    else                      setListAdapter(new DecryptingConversationListAdapter(this, null, masterSecret));
//  }

//  private void deleteThread(long threadId) {
//    AlertDialog.Builder builder = new AlertDialog.Builder(this);
//    builder.setTitle("Delete Thread Confirmation");
//    builder.setIcon(android.R.drawable.ic_dialog_alert);
//    builder.setCancelable(true);
//    builder.setMessage("Are you sure that you want to permanently delete this conversation?");
//    builder.setPositiveButton(R.string.yes, new DeleteThreadListener(threadId));
//    builder.setNegativeButton(R.string.no, null);
//    builder.show();
//  }


//  private class DeleteThreadListener implements DialogInterface.OnClickListener {
//    private final long threadId;
//
//    public DeleteThreadListener(long threadId) {
//      this.threadId = threadId;
//    }
//
//    public void onClick(DialogInterface dialog, int which) {
//      if (threadId > 0) {
//        DatabaseFactory.getThreadDatabase(ConversationListActivity.this).deleteConversation(threadId);
//      }
//    }
//  };

//  private class NewKeyReceiver extends BroadcastReceiver {
//    @Override
//    public void onReceive(Context context, Intent intent) {
//      Log.w("securesms", "Got a broadcast...");
//      initializeWithMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
//    }
//  }

//  private class KillActivityReceiver extends BroadcastReceiver {
//    @Override
//    public void onReceive(Context arg0, Intent arg1) {
//      finish();
//    }
//  }

  //  private class SettingsClickListener implements View.OnClickListener {
  //    public void onClick(View v) {
  //      startActivity(new Intent(SecureSMS.this, ApplicationPreferencesActivity.class));
  //    }
  //  }


//  private class ExportHandler extends Handler implements Runnable {
//    private static final int ERROR_NO_SD = 0;
//    private static final int ERROR_IO    = 1;
//    private static final int COMPLETE    = 2;
//
//    private static final int TASK_EXPORT = 0;
//    private static final int TASK_IMPORT = 1;
//
//    private int task;
//    private ProgressDialog progressDialog;
//
//    public void run() {
//      try {
//        switch (task) {
//        case TASK_EXPORT: ApplicationExporter.exportToSd(ConversationListActivity.this);   break;
//        case TASK_IMPORT: ApplicationExporter.importFromSd(ConversationListActivity.this); break;
//        }
//      } catch (NoExternalStorageException e) {
//        Log.w("SecureSMS", e);
//        this.obtainMessage(ERROR_NO_SD).sendToTarget();
//        return;
//      } catch (IOException e) {
//        Log.w("SecureSMS", e);
//        this.obtainMessage(ERROR_IO).sendToTarget();
//        return;
//      }
//
//      this.obtainMessage(COMPLETE).sendToTarget();
//    }
//
//    private void continueExport() {
//      task           = TASK_EXPORT;
//      progressDialog = new ProgressDialog(ConversationListActivity.this);
//      progressDialog.setTitle("Exporting Database and Keys");
//      progressDialog.setMessage("Exporting your SMS database, keys, and settings...");
//      progressDialog.setCancelable(false);
//      progressDialog.setIndeterminate(true);
//      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//      progressDialog.show();
//      new Thread(this).start();
//    }
//
//    private void continueImport() {
//      task           = TASK_IMPORT;
//      progressDialog = new ProgressDialog(ConversationListActivity.this);
//      progressDialog.setTitle("Importing Database and Keys");
//      progressDialog.setMessage("Importnig your SMS database, keys, and settings...");
//      progressDialog.setCancelable(false);
//      progressDialog.setIndeterminate(true);
//      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//      progressDialog.show();
//
//      initializeWithMasterSecret(null);
//      Intent clearKeyIntent = new Intent(KeyCachingService.CLEAR_KEY_ACTION, null, ConversationListActivity.this, KeyCachingService.class);
//      startService(clearKeyIntent);
//
//      DatabaseFactory.getInstance(ConversationListActivity.this).close();
//      new Thread(this).start();
//    }
//
//    public void importFromSd() {
//      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(ConversationListActivity.this);
//      alertBuilder.setTitle("Import Database and Settings?");
//      alertBuilder.setMessage("Import TextSecure database, keys, and settings from the SD Card?\n\nWARNING: This will clobber any existing messages, keys, and settings!");
//      alertBuilder.setCancelable(false);
//      alertBuilder.setPositiveButton("Import", new DialogInterface.OnClickListener() {
//        public void onClick(DialogInterface dialog, int which) {
//          continueImport();
//        }
//      });
//      alertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//        public void onClick(DialogInterface dialog, int which) {
//        }
//      });
//      alertBuilder.create().show();
//    }
//
//    public void export() {
//      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(ConversationListActivity.this);
//      alertBuilder.setTitle("Export Database?");
//      alertBuilder.setMessage("Export TextSecure database, keys, and settings to the SD Card?");
//      alertBuilder.setCancelable(false);
//      alertBuilder.setPositiveButton("Export", new DialogInterface.OnClickListener() {
//        public void onClick(DialogInterface dialog, int which) {
//          continueExport();
//        }
//      });
//      alertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//        public void onClick(DialogInterface dialog, int which) {
//        }
//      });
//      alertBuilder.create().show();
//    }
//
//    @Override
//    public void handleMessage(Message message) {
//      switch (message.what) {
//      case ERROR_NO_SD:
//        Toast.makeText(ConversationListActivity.this, "No SD card found!", Toast.LENGTH_LONG).show();
//        break;
//      case ERROR_IO:
//        Toast.makeText(ConversationListActivity.this, "Error exporting to SD!", Toast.LENGTH_LONG).show();
//        break;
//      case COMPLETE:
//        switch (task) {
//        case TASK_IMPORT:
//          Toast.makeText(ConversationListActivity.this, "Import Successful!", Toast.LENGTH_LONG).show();
////          addConversationItems();
//          promptForPassphrase();
//          break;
//        case TASK_EXPORT:
//          Toast.makeText(ConversationListActivity.this, "Export Successful!", Toast.LENGTH_LONG).show();
//          break;
//        }
//        break;
//      }
//
//      progressDialog.dismiss();
//    }
//  }

//  private class ContactUpdateObserver extends ContentObserver {
//    public ContactUpdateObserver() {
//      super(null);
//    }
//
//    @Override
//    public void onChange(boolean selfChange) {
//      super.onChange(selfChange);
//      Log.w("SesureSMS", "Got contact update, clearing cache...");
//      RecipientFactory.clearCache();
//    }
//  }

//  private class MigrationHandler extends Handler implements Runnable {
//    private ProgressDialog progressDialog;
//
//    public void run() {
//      SmsMigrator.migrateDatabase(ConversationListActivity.this, masterSecret, this);
//    }
//
//    private void continueMigration() {
//      progressDialog = new ProgressDialog(ConversationListActivity.this);
//      progressDialog.setTitle("Migrating Database");
//      progressDialog.setMessage("Migrating your SMS database...");
//      progressDialog.setMax(10000);
//      progressDialog.setCancelable(false);
//      progressDialog.setIndeterminate(false);
//      progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//      progressDialog.show();
//      new Thread(this).start();
//    }
//
//    private void cancelMigration() {
//      ConversationListActivity.this.getSharedPreferences("SecureSMS", MODE_PRIVATE).edit().putBoolean("migrated", true).commit();
//      ConversationListActivity.this.migrateDatabaseComplete();
//    }
//
//    public void migrate() {
//      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(ConversationListActivity.this);
//      alertBuilder.setTitle("Copy System Text Message Database?");
//      alertBuilder.setMessage("Current versions of TextSecure use an encrypted database that is separate from the default system database.  Would you like to copy your existing text messages into TextSecure's encrypted database?  Your default system database will be unaffected.");
//      alertBuilder.setCancelable(false);
//      alertBuilder.setPositiveButton("Copy", new DialogInterface.OnClickListener() {
//        public void onClick(DialogInterface dialog, int which) {
//          continueMigration();
//        }
//      });
//      alertBuilder.setNegativeButton("Don't copy", new DialogInterface.OnClickListener() {
//        public void onClick(DialogInterface dialog, int which) {
//          cancelMigration();
//        }
//      });
//      alertBuilder.create().show();
//    }
//
//    @Override
//    public void handleMessage(Message message) {
//      switch (message.what) {
//      case SmsMigrator.PROGRESS_UPDATE:
//        progressDialog.incrementProgressBy(message.arg1);
//        progressDialog.setSecondaryProgress(0);
//        break;
//      case SmsMigrator.SECONDARY_PROGRESS_UPDATE:
//        progressDialog.incrementSecondaryProgressBy(message.arg1);
//        break;
//      case SmsMigrator.COMPLETE:
//        progressDialog.dismiss();
//        ConversationListActivity.this.migrateDatabaseComplete();
//        break;
//      }
//    }
//  }
//

}
