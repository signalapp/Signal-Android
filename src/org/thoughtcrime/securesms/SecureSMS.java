/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.ApplicationExporter;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.SmsMigrator;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.lang.BhoTyper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.Eula;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * 
 * The main Activity for TextSecure.  Manages the conversation list, search
 * access to the conversation list, and import/export handling.
 * 
 * @author Moxie Marlinspike
 */

public class SecureSMS extends ListActivity {
	
  private static final int MENU_SEND_KEY                = 1;
  private static final int MENU_PASSPHRASE_KEY          = 2;
  private static final int MENU_PREFERENCES_KEY         = 3;
  private static final int MENU_EXPORT                  = 5;
  private static final int MENU_IMPORT                  = 6;
  private static final int MENU_SEARCH                  = 11;
  private static final int MENU_CLEAR_PASSPHRASE	      = 12;
  private static final int MENU_DELETE_SELECTED_THREADS = 13;
  private static final int MENU_BATCH_MODE              = 14;
	
  private static final int MENU_EXIT_BATCH              = 15;
  private static final int MENU_SELECT_ALL_THREADS      = 16;
  private static final int MENU_CLEAR_SELECTION         = 17;
	
  private static final int VIEW_THREAD_ID     = 100;
  private static final int VIEW_CONTACT_ID    = 101;
  private static final int DELETE_THREAD_ID   = 102;
  private static final int ADD_CONTACT_ID     = 103;
	
  private EditText searchBox;
  private ConversationHeaderView headerView;
  private MasterSecret masterSecret;
  private KillActivityReceiver killActivityReceiver;
  private NewKeyReceiver receiver;
  private boolean havePromptedForPassphrase = false;
  private boolean batchMode                 = false;
  
  BhoTyper bho;
	
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.main);
    
    bho = new BhoTyper(this, getWindow().getDecorView().findViewById(android.R.id.content));
    
    initializeKillReceiver();
    initializeSenderReceiverService();
    initializeResources();
    initializeSearchListener();
    registerForContextMenu(getListView());
    registerForContactsUpdates();
  }
  
  @Override
  public void onPause() {
    super.onPause();
    
    if (receiver != null) {
      Log.w("securesms", "Unregistering receiver...");
      unregisterReceiver(receiver);
      receiver = null;
    }       
  }
  
  @Override
  public void onResume() {
    super.onResume();
    
    Log.w("securesms", "restart called...");
    initializeColors();
    Eula.showEula(this);
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
    super.onPrepareOptionsMenu(menu);
		
    menu.clear();
		
    if (!batchMode) prepareNormalMenu(menu);
    else            prepareBatchModeMenu(menu);
		
    return true;
  }
	
  private void prepareNormalMenu(Menu menu) {
    menu.add(0, MENU_BATCH_MODE, Menu.NONE, R.string.batch_mode).setIcon(android.R.drawable.ic_menu_share);
		
    if (masterSecret != null) menu.add(0, MENU_SEND_KEY, Menu.NONE, R.string.secure_session).setIcon(R.drawable.ic_lock_message_sms);
    else           			  menu.add(0, MENU_PASSPHRASE_KEY, Menu.NONE, R.string.enter_passphrase).setIcon(R.drawable.ic_lock_message_sms);

    menu.add(0, MENU_SEARCH, Menu.NONE, R.string.search).setIcon(android.R.drawable.ic_menu_search);
    menu.add(0, MENU_PREFERENCES_KEY, Menu.NONE, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);	
		
    SubMenu importExportMenu = menu.addSubMenu(R.string.import_export).setIcon(android.R.drawable.ic_menu_save);
    importExportMenu.add(0, MENU_EXPORT, Menu.NONE, R.string.export_to_sd_card).setIcon(android.R.drawable.ic_menu_save);
    importExportMenu.add(0, MENU_IMPORT, Menu.NONE, R.string.import_from_sd_card).setIcon(android.R.drawable.ic_menu_revert);
		
    SubMenu moreMenu = menu.addSubMenu(R.string.more).setIcon(android.R.drawable.ic_menu_more);

    if (masterSecret != null)
      moreMenu.add(0, MENU_CLEAR_PASSPHRASE, Menu.NONE, R.string.clear_passphrase).setIcon(android.R.drawable.ic_menu_close_clear_cancel);

  }
	
  private void prepareBatchModeMenu(Menu menu) {
    menu.add(0, MENU_EXIT_BATCH, Menu.NONE, R.string.normal_mode).setIcon(android.R.drawable.ic_menu_set_as);
    menu.add(0, MENU_DELETE_SELECTED_THREADS, Menu.NONE, R.string.delete_selected).setIcon(android.R.drawable.ic_menu_delete);
    menu.add(0, MENU_SELECT_ALL_THREADS, Menu.NONE, R.string.select_all).setIcon(android.R.drawable.ic_menu_add);
    menu.add(0, MENU_CLEAR_SELECTION, Menu.NONE, R.string.unselect_all).setIcon(android.R.drawable.ic_menu_revert);
  }
	
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
		
    switch (item.getItemId()) {
    case MENU_SEND_KEY:
      Intent intent = new Intent(this, SendKeyActivity.class);
      intent.putExtra("master_secret", masterSecret);
			
      startActivity(intent);
      return true;
    case MENU_PASSPHRASE_KEY:
      promptForPassphrase();
      return true;
    case MENU_BATCH_MODE:
      initiateBatchMode();
      return true;
    case MENU_PREFERENCES_KEY:
      Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
      preferencesIntent.putExtra("master_secret", masterSecret);
      startActivity(preferencesIntent);
      return true;
    case MENU_EXPORT:
      ExportHandler exportHandler = new ExportHandler();
      exportHandler.export();
      return true;
    case MENU_IMPORT:
      ExportHandler importHandler = new ExportHandler();
      importHandler.importFromSd();
      return true;
    case MENU_SEARCH:
      findViewById(R.id.search_text).setVisibility(View.VISIBLE);
      findViewById(R.id.search_close).setVisibility(View.VISIBLE);
      findViewById(R.id.search_text).requestFocus();
      return true;
    case MENU_DELETE_SELECTED_THREADS: deleteSelectedThreads(); return true;
    case MENU_SELECT_ALL_THREADS:      selectAllThreads();      return true;
    case MENU_CLEAR_SELECTION:         unselectAllThreads();    return true;
    case MENU_EXIT_BATCH:              stopBatchMode();         return true;
    case MENU_CLEAR_PASSPHRASE:
      Intent keyService = new Intent(this, KeyCachingService.class);
      keyService.setAction(KeyCachingService.CLEAR_KEY_ACTION);
      startService(keyService);
      addConversationItems();
      promptForPassphrase();
      //			finish();
      return true;
    }
		
    return false;
  }
	
	
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.w("SecureSMS", "Got onNewIntent...");
    createConversationIfNecessary(intent);
  }
	
  public void eulaComplete() {
    clearNotifications();
    initializeReceivers();
    checkCachingService();
  }	
	
  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    if (((AdapterView.AdapterContextMenuInfo)menuInfo).position > 0) {
      Cursor cursor         = ((CursorAdapter)this.getListAdapter()).getCursor();
      String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
      Recipients recipients = RecipientFactory.getRecipientsForIds(this, recipientId);

      menu.add(0, VIEW_THREAD_ID, Menu.NONE, R.string.view_thread);
			
      if (recipients.isSingleRecipient()) {
        if (recipients.getPrimaryRecipient().getName() != null) {
          menu.add(0, VIEW_CONTACT_ID, Menu.NONE, R.string.view_contact);
        } else {
          menu.add(0, ADD_CONTACT_ID, Menu.NONE, R.string.add_to_contacts);
        }
      }
			
      menu.add(0, DELETE_THREAD_ID, Menu.NONE, R.string.delete_thread);			
    }
  }
	
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    Cursor cursor         = ((CursorAdapter)this.getListAdapter()).getCursor();
    long threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
    String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
    Recipients recipients = RecipientFactory.getRecipientsForIds(this, recipientId);

    switch(item.getItemId()) {
    case VIEW_THREAD_ID:
      createConversation(threadId, recipients);
      return true;
    case VIEW_CONTACT_ID:
      viewContact(recipients.getPrimaryRecipient());
      return true;
    case ADD_CONTACT_ID:
      addContact(recipients.getPrimaryRecipient());
      return true;
    case DELETE_THREAD_ID:
      deleteThread(threadId);
      return true;
    }
    return false;
  }
	
  private void initiateBatchMode() {
    this.batchMode = true;
    ((ConversationListAdapter)this.getListAdapter()).initializeBatchMode(batchMode);
  }
	
  private void stopBatchMode() {
    this.batchMode = false;
    ((ConversationListAdapter)this.getListAdapter()).initializeBatchMode(batchMode);		
  }
	
  private void selectAllThreads() {
    ((ConversationListAdapter)this.getListAdapter()).selectAllThreads();
  }
	
  private void unselectAllThreads() {
    ((ConversationListAdapter)this.getListAdapter()).unselectAllThreads();
  }
	
  private void deleteSelectedThreads() {
    AlertDialog.Builder alert = new AlertDialog.Builder(this);
    alert.setIcon(android.R.drawable.ic_dialog_alert);
    alert.setTitle(R.string.delete_threads_);
    alert.setMessage(R.string.are_you_sure_you_wish_to_delete_all_selected_conversation_threads_);
    alert.setCancelable(true);
    alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {			
      public void onClick(DialogInterface dialog, int which) {
        Set<Long> selectedConversations = ((ConversationListAdapter)getListAdapter()).getBatchSelections();
				
        if (!selectedConversations.isEmpty())
          DatabaseFactory.getThreadDatabase(SecureSMS.this).deleteConversations(selectedConversations);
      }
    });
    alert.setNegativeButton(R.string.cancel, null);
    alert.show();
  }
	
  private void registerForContactsUpdates() {
    Log.w("SecureSMS", "Registering for contacts update...");
    getContentResolver().registerContentObserver(ContactAccessor.getInstance().getContactsUri(), true, new ContactUpdateObserver());
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
	
  private void addContact(Recipient recipient) {
    RecipientFactory.getRecipientProvider().addContact(this, recipient);
  }
	
  private void viewContact(Recipient recipient) {
    if (recipient.getPersonId() > 0) {
      RecipientFactory.getRecipientProvider().viewContact(this, recipient);
    }
  }
	
  private void clearNotifications() {
    NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    manager.cancel(KeyCachingService.NOTIFICATION_ID);
  }
	
  private void initializeResources() {
    //		ImageView settingsImage = (ImageView)findViewById(R.id.settings_button);
    //		settingsImage.setOnClickListener(new SettingsClickListener());
  }
	
  private void initializeKillReceiver() {
    killActivityReceiver = new KillActivityReceiver();
    registerReceiver(killActivityReceiver, new IntentFilter(KeyCachingService.PASSPHRASE_EXPIRED_EVENT), 
                     KeyCachingService.KEY_PERMISSION, null);
  }
	
  private void initializeSenderReceiverService() {
    Intent smsSenderIntent = new Intent(SendReceiveService.SEND_SMS_ACTION, null, this, SendReceiveService.class);
    Intent mmsSenderIntent = new Intent(SendReceiveService.SEND_MMS_ACTION, null, this, SendReceiveService.class);
    startService(smsSenderIntent);
    startService(mmsSenderIntent);
  }
		
  private void checkCachingService() {
    Log.w("securesms", "Checking caching service...");
    Intent bindIntent = new Intent(this, KeyCachingService.class);
    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void migrateDatabaseComplete() {
    if (masterSecret != null)
      DecryptingQueue.schedulePendingDecrypts(this, masterSecret);
  }
	
  private void migrateDatabase() {
    MigrationHandler mh = new MigrationHandler();
    mh.migrate();
  }
	
  private boolean isMigrated() {
    return this.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).getBoolean("migrated", false);
  }
	
  private void initializeWithMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;			
				
    if (masterSecret != null) {
      if (!IdentityKeyUtil.hasIdentityKey(this))            initializeIdentityKeys();
      if (!MasterSecretUtil.hasAsymmericMasterSecret(this)) initializeAsymmetricMasterSecret();
      if (!isMigrated())                                    migrateDatabase();
      else                                                  DecryptingQueue.schedulePendingDecrypts(this, masterSecret);
    }
		
    addNewMessageItem();
    addConversationItems();
    createConversationIfNecessary(this.getIntent());
  }
	
  private void initializeAsymmetricMasterSecret() {
    new Thread(new AsymmetricMasteSecretInitializer()).start();
  }
	
  private void initializeIdentityKeys() {
    new Thread(new IdentityKeyInitializer()).start();
  }
	
  private void initializeSearchListener() {
    SearchTextListener listener = new SearchTextListener();
    searchBox                   = (EditText)findViewById(R.id.search_text);
    searchBox.addTextChangedListener(listener);
    this.getListView().setOnKeyListener(listener);
  }
	
  private void initializeReceivers() {
    Log.w("securesms", "Registering receiver...");
    receiver            = new NewKeyReceiver();
    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);

    registerReceiver(receiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void initializeColors() {
    if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(ApplicationPreferencesActivity.DARK_THREADS_PREF, true)) {
      this.getListView().setBackgroundColor(Color.WHITE);
      this.getListView().setCacheColorHint(Color.WHITE);
      this.getListView().setDivider(new ColorDrawable(Color.parseColor("#cccccc")));
      this.getListView().setDividerHeight(1);
    } else {
      this.getListView().setBackgroundColor(Color.BLACK);
      this.getListView().setCacheColorHint(Color.BLACK);
      this.getListView().setDivider(this.getResources().getDrawable(R.drawable.dark_divider));
      this.getListView().setDividerHeight(1);
    }		
		
    if (headerView != null) {
      headerView.initializeColors();
      headerView.setBackgroundColor(Color.TRANSPARENT);
    }
  }
	
  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    if (position == 0) {
      createConversation(-1, null);
    } else if (v instanceof ConversationHeaderView) {
      ConversationHeaderView headerView = (ConversationHeaderView) v;
      createConversation(headerView.getThreadId(), headerView.getRecipients());
    }
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

  private void addNewMessageItem() {
    ListView listView = getListView();
    if (listView.getHeaderViewsCount() > 0) return;
		
    ArrayList<Recipient> dummyList        = new ArrayList<Recipient>();
    dummyList.add(new Recipient(getString(R.string.new_message), null, null));
		
    Recipients recipients                 = new Recipients(dummyList);		
    headerView                            = new ConversationHeaderView(this, true);
    MessageRecord messageRecord           = new MessageRecord(-1, recipients, 0, 0, true, -1);
    messageRecord.setBody(getString(R.string.compose_new_message_));
    headerView.set(messageRecord, false);
    //		headerView.setBackgroundColor(Color.TRANSPARENT);
		
    listView.addHeaderView(headerView, null, true);
  }
	
  private void addConversationItems() {
    Cursor cursor = DatabaseFactory.getThreadDatabase(this).getConversationList();
    startManagingCursor(cursor);
    	
    if (masterSecret == null) setListAdapter(new ConversationListAdapter(this, cursor));
    else 					  setListAdapter(new DecryptingConversationListAdapter(this, cursor, masterSecret));
  }

  private void deleteThread(long threadId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.delete_thread_confirmation);
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage(R.string.are_you_sure_that_you_want_to_permanently_delete_this_conversation_);
    builder.setPositiveButton(R.string.yes, new DeleteThreadListener(threadId));
    builder.setNegativeButton(R.string.no, null);
    builder.show();
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
    
  private ServiceConnection serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
        KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
        MasterSecret masterSecret            = keyCachingService.getMasterSecret();

        initializeWithMasterSecret(masterSecret);

        if (masterSecret == null && !havePromptedForPassphrase) 
          promptForPassphrase();	
			
        Intent cachingIntent = new Intent(SecureSMS.this, KeyCachingService.class);		
        startService(cachingIntent);

        try {
          SecureSMS.this.unbindService(this);			
        } catch (IllegalArgumentException iae) {
          Log.w("SecureSMS", iae);
        }
      }

      public void onServiceDisconnected(ComponentName name) {}
    };
		
  private class DeleteThreadListener implements DialogInterface.OnClickListener {
    private final long threadId;
		
    public DeleteThreadListener(long threadId) {
      this.threadId = threadId;
    }
		
    public void onClick(DialogInterface dialog, int which) {
      if (threadId > 0) {
        DatabaseFactory.getThreadDatabase(SecureSMS.this).deleteConversation(threadId);
      }		
    }		
  };
	
  private class NewKeyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w("securesms", "Got a broadcast...");
      initializeWithMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
    }
  }
	
  private class KillActivityReceiver extends BroadcastReceiver {
    @Override
      public void onReceive(Context arg0, Intent arg1) {
      finish();
    }
  }
	
  //	private class SettingsClickListener implements View.OnClickListener {
  //		public void onClick(View v) {
  //			startActivity(new Intent(SecureSMS.this, ApplicationPreferencesActivity.class));
  //		}
  //	}
	
  private class IdentityKeyInitializer implements Runnable {
    public void run() {
      IdentityKeyUtil.generateIdentityKeys(SecureSMS.this, masterSecret);
    }
  }
	
  private class AsymmetricMasteSecretInitializer implements Runnable {
    public void run() {
      MasterSecretUtil.generateAsymmetricMasterSecret(SecureSMS.this, masterSecret);
    }
  }
	
  private class ExportHandler extends Handler implements Runnable {
    private static final int ERROR_NO_SD = 0;
    private static final int ERROR_IO    = 1;
    private static final int COMPLETE    = 2;
		
    private static final int TASK_EXPORT = 0;
    private static final int TASK_IMPORT = 1;
		
    private int task;
    private ProgressDialog progressDialog;
		
    public void run() {
      try {
        switch (task) {
        case TASK_EXPORT: ApplicationExporter.exoprtToSd(SecureSMS.this);   break;
        case TASK_IMPORT: ApplicationExporter.importFromSd(SecureSMS.this); break;
        }
      } catch (NoExternalStorageException e) {
        Log.w("SecureSMS", e);
        this.obtainMessage(ERROR_NO_SD).sendToTarget();
        return;
      } catch (IOException e) {
        Log.w("SecureSMS", e);
        this.obtainMessage(ERROR_IO).sendToTarget();
        return;
      }
			
      this.obtainMessage(COMPLETE).sendToTarget();
    }

    private void continueExport() {			
      task           = TASK_EXPORT;
      progressDialog = new ProgressDialog(SecureSMS.this);
      progressDialog.setTitle(R.string.exporting_database_and_keys);
      progressDialog.setMessage(getString(R.string.exporting_your_sms_database_keys_and_settings_));
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(true);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progressDialog.show();
      new Thread(this).start();
    }
		
    private void continueImport() {
      task           = TASK_IMPORT;
      progressDialog = new ProgressDialog(SecureSMS.this);
      progressDialog.setTitle(R.string.importing_database_and_keys);
      progressDialog.setMessage(getString(R.string.importing_your_sms_database_keys_and_settings_));
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(true);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progressDialog.show();
			
      initializeWithMasterSecret(null);
      Intent clearKeyIntent = new Intent(KeyCachingService.CLEAR_KEY_ACTION, null, SecureSMS.this, KeyCachingService.class);
      startService(clearKeyIntent);
			
      DatabaseFactory.getInstance(SecureSMS.this).close();
      new Thread(this).start();			
    }
		
    public void importFromSd() {
      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(SecureSMS.this);
      alertBuilder.setTitle(R.string.import_database_and_settings_);
      alertBuilder.setMessage(getString(R.string.import_textsecure_database_keys_and_settings_from_the_sd_card_1) + "\n\n" + getString(R.string.import_textsecure_database_keys_and_settings_from_the_sd_card_2));
      alertBuilder.setCancelable(false);
      alertBuilder.setPositiveButton("Import", new DialogInterface.OnClickListener() {				
        public void onClick(DialogInterface dialog, int which) {
          continueImport();
        }
      });
      alertBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
        }
      });
      alertBuilder.create().show();			
    }
		
    public void export() {
      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(SecureSMS.this);
      alertBuilder.setTitle(R.string.export_database_);
      alertBuilder.setMessage(R.string.export_textsecure_database_keys_and_settings_to_the_sd_card_);
      alertBuilder.setCancelable(false);
      alertBuilder.setPositiveButton(R.string.export, new DialogInterface.OnClickListener() {				
        public void onClick(DialogInterface dialog, int which) {
          continueExport();
        }
      });
      alertBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
        }
      });
      alertBuilder.create().show();
    }
		
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
      case ERROR_NO_SD:
        Toast.makeText(SecureSMS.this, R.string.no_sd_card_found_, Toast.LENGTH_LONG).show();
        break;
      case ERROR_IO:
        Toast.makeText(SecureSMS.this, R.string.error_exporting_to_sd_, Toast.LENGTH_LONG).show();
        break;
      case COMPLETE:
        switch (task) {
        case TASK_IMPORT:
          Toast.makeText(SecureSMS.this, R.string.import_successful_, Toast.LENGTH_LONG).show();
          addConversationItems();
          promptForPassphrase();
          break;
        case TASK_EXPORT:
          Toast.makeText(SecureSMS.this, R.string.export_successful_, Toast.LENGTH_LONG).show();
          break;
        }
        break;
      }
			
      progressDialog.dismiss();
    }
  }
	
  private class SearchTextListener extends Handler implements TextWatcher, Runnable, View.OnClickListener, View.OnKeyListener {		
    private final ImageView closeButton;
    private final ProgressBar progressDialog;
    private final KeyCharacterMap keyMap;
		
    private int outstandingThreads;
		
    public SearchTextListener() {
      closeButton        = (ImageView)findViewById(R.id.search_close);
      progressDialog     = (ProgressBar)findViewById(R.id.search_progress);
      keyMap             = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);
      outstandingThreads = 0;
			
      closeButton.setOnClickListener(this);
    }
		
    private Cursor getCursorForFilter(String text) {
      if (text.length() > 0) {
        List<String> numbers = ContactAccessor.getInstance().getNumbersForThreadSearchFilter(text, getContentResolver());
        return DatabaseFactory.getThreadDatabase(SecureSMS.this).getFilteredConversationList(numbers);
      } else {
        return DatabaseFactory.getThreadDatabase(SecureSMS.this).getConversationList();
      }			
    }
				
    public void afterTextChanged(Editable arg0) {
      synchronized (this) {
        if (outstandingThreads == 0)
          progressDialog.setVisibility(View.VISIBLE);
				
        outstandingThreads++;
      }
			
      new Thread(this).start();
    }
		
    @Override
    public void handleMessage(Message message) {
      Cursor cursor = (Cursor)message.obj;
			
      if (cursor != null)
        startManagingCursor(cursor);
			
      if (getListAdapter() != null)
        ((CursorAdapter)getListAdapter()).changeCursor(cursor);	
			
      synchronized (this) {
        outstandingThreads--;
				
        if (outstandingThreads == 0)
          progressDialog.setVisibility(View.GONE);
      }
    }
		
    public void run() {
      String text   = searchBox.getText().toString();
      Cursor cursor = getCursorForFilter(text);			
      this.obtainMessage(0, cursor).sendToTarget();
    }

    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

    public void onClick(View v) {
      searchBox.setVisibility(View.GONE);
      closeButton.setVisibility(View.GONE);
      searchBox.setText("");
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyMap.isPrintingKey(keyCode))) {
        int character = keyMap.get(keyCode, event.getMetaState());
        searchBox.setVisibility(View.VISIBLE);
        closeButton.setVisibility(View.VISIBLE);
        searchBox.setText(new String(Character.toChars(character)));
        searchBox.requestFocus();
        return true;
      }
			
      return false;
    }
  }
	
  private class ContactUpdateObserver extends ContentObserver {
    public ContactUpdateObserver() {
      super(null);
    }

    @Override
    public void onChange(boolean selfChange) {
      super.onChange(selfChange);
      Log.w("SesureSMS", "Got contact update, clearing cache...");
      RecipientFactory.clearCache();
    }
  }
	
  private class MigrationHandler extends Handler implements Runnable {
    private ProgressDialog progressDialog;

    public void run() {
      SmsMigrator.migrateDatabase(SecureSMS.this, masterSecret, this);	
    }

    private void continueMigration() {			
      progressDialog = new ProgressDialog(SecureSMS.this);
      progressDialog.setTitle(R.string.migrating_database);
      progressDialog.setMessage(getString(R.string.migrating_your_sms_database_));
      progressDialog.setMax(10000);
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(false);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      progressDialog.show();
      new Thread(this).start();
    }
		
    private void cancelMigration() {
      SecureSMS.this.getSharedPreferences("SecureSMS", MODE_PRIVATE).edit().putBoolean("migrated", true).commit();
      SecureSMS.this.migrateDatabaseComplete();
    }
		
    public void migrate() {
      AlertDialog.Builder alertBuilder = new AlertDialog.Builder(SecureSMS.this);
      alertBuilder.setTitle(R.string.copy_system_text_message_database_);
      alertBuilder.setMessage(R.string.current_versions_of_textsecure_use_an_encrypted_database);
      alertBuilder.setCancelable(false);
      alertBuilder.setPositiveButton(R.string.copy, new DialogInterface.OnClickListener() {				
        public void onClick(DialogInterface dialog, int which) {
          continueMigration();
        }
      });
      alertBuilder.setNegativeButton(R.string.don_t_copy, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          cancelMigration();
        }
      });
      alertBuilder.create().show();
    }
		
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
      case SmsMigrator.PROGRESS_UPDATE:
        progressDialog.incrementProgressBy(message.arg1);
        progressDialog.setSecondaryProgress(0);
        break;
      case SmsMigrator.SECONDARY_PROGRESS_UPDATE:
        progressDialog.incrementSecondaryProgressBy(message.arg1);
        break;
      case SmsMigrator.COMPLETE:
        progressDialog.dismiss();
        SecureSMS.this.migrateDatabaseComplete();
        break;
      }
    }
  }
}
    
 