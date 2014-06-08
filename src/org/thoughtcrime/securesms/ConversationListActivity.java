/**
 * Copyright (C) 2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConversationListActivity extends PassphraseRequiredSherlockFragmentActivity
    implements ConversationListFragment.ConversationSelectedListener,
               ListView.OnItemClickListener
  {
  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment fragment;
  private MasterSecret masterSecret;
  private DrawerLayout drawerLayout;
  private DrawerToggle drawerToggle;
  private ListView     drawerList;

  @Override
  public void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    setContentView(R.layout.conversation_list_activity);

    getSupportActionBar().setTitle(R.string.app_name);

    initializeNavigationDrawer();
    initializeSenderReceiverService();
    initializeResources();
    initializeContactUpdatesReceiver();

    DirectoryRefreshListener.schedule(this);
  }

  @Override
  public void onPostCreate(Bundle bundle) {
    super.onPostCreate(bundle);
    drawerToggle.syncState();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    initializeDefaultMessengerCheck();
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

    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public void onItemClick(AdapterView parent, View view, int position, long id) {
    String[] values = getResources().getStringArray(R.array.navigation_drawer_values);
    String selected = values[position];

    Intent intent;

    if (selected.equals("import_export")) {
      intent = new Intent(this, ImportExportActivity.class);
      intent.putExtra("master_secret", masterSecret);
    } else if (selected.equals("my_identity_key")) {
      intent = new Intent(this, ViewLocalIdentityActivity.class);
      intent.putExtra("master_secret", masterSecret);
    } else if (selected.equals("contact_identity_keys")) {
      intent = new Intent(this, ReviewIdentitiesActivity.class);
      intent.putExtra("master_secret", masterSecret);
    } else {
      return;
    }

    drawerLayout.closeDrawers();
    startActivity(intent);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int defaultType = ThreadDatabase.DistributionTypes.DEFAULT;

    switch (item.getItemId()) {
    case R.id.menu_new_message:       openSingleContactSelection();   return true;
    case R.id.menu_new_group:         createGroup();                  return true;
    case R.id.menu_settings:          handleDisplaySettings();        return true;
    case R.id.menu_clear_passphrase:  handleClearPassphrase();        return true;
    case R.id.menu_mark_all_read:     handleMarkAllRead();            return true;
    case android.R.id.home:           handleNavigationDrawerToggle(); return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
    createConversation(threadId, recipients, distributionType);
  }

  private void createGroup() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    intent.putExtra("master_secret", masterSecret);
    startActivity(intent);
  }

  private void openSingleContactSelection() {
    Intent intent = new Intent(this, NewConversationActivity.class);
    intent.putExtra(NewConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
    startActivity(intent);
  }

  private void createConversation(long threadId, Recipients recipients, int distributionType) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.toIdString());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

    startActivity(intent);
  }

  private void handleNavigationDrawerToggle() {
    if (drawerLayout.isDrawerOpen(drawerList)) {
      drawerLayout.closeDrawer(drawerList);
    } else {
      drawerLayout.openDrawer(drawerList);
    }
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    preferencesIntent.putExtra("master_secret", masterSecret);
    startActivity(preferencesIntent);
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

  private void initializeNavigationDrawer() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);

    int[]    attributes = new int[]   {R.attr.navigation_drawer_icons, R.attr.navigation_drawer_shadow};
    String[] from       = new String[]{"navigation_icon", "navigation_text"      };
    int[]    to         = new int[]   {R.id.navigation_icon, R.id.navigation_text};

    TypedArray iconArray  = obtainStyledAttributes(attributes);
    int iconArrayResource = iconArray.getResourceId(0, -1);
    TypedArray icons      = getResources().obtainTypedArray(iconArrayResource);
    String[] text         = getResources().getStringArray(R.array.navigation_drawer_text);

    List<HashMap<String, String>> items = new ArrayList<HashMap<String, String>>();

    for(int i = 0; i < text.length; i++){
      HashMap<String, String> item = new HashMap<String, String>();
      item.put("navigation_icon", Integer.toString(icons.getResourceId(i, -1)));
      item.put("navigation_text", text[i]);
      items.add(item);
    }

    DrawerLayout drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
    drawerToggle = new DrawerToggle(this, drawerLayout,
                                    R.drawable.ic_drawer,
                                    R.string.conversation_list__drawer_open,
                                    R.string.conversation_list__drawer_close);

    drawerLayout.setDrawerListener(drawerToggle);

    ListView drawer           = (ListView)findViewById(R.id.left_drawer);
    SimpleAdapter adapter     = new SimpleAdapter(this, items, R.layout.navigation_drawer_item, from, to);

    drawerLayout.setDrawerShadow(iconArray.getDrawable(1), GravityCompat.START);
    drawer.setAdapter(adapter);
    drawer.setOnItemClickListener(this);

    iconArray.recycle();
    icons.recycle();
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && TextSecurePreferences.isScreenSecurityEnabled(this)) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                           WindowManager.LayoutParams.FLAG_SECURE);
    }

    this.drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
    this.drawerList   = (ListView)findViewById(R.id.left_drawer);
    this.masterSecret = getIntent().getParcelableExtra("master_secret");

    this.fragment = (ConversationListFragment)this.getSupportFragmentManager()
        .findFragmentById(R.id.fragment_content);

    this.fragment.setMasterSecret(masterSecret);
  }

  private void initializeDefaultMessengerCheck() {
    if (!TextSecurePreferences.hasPromptedDefaultSmsProvider(this) && !Util.isDefaultSmsProvider(this)) {
      TextSecurePreferences.setPromptedDefaultSmsProvider(this, true);
      Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
      intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
      startActivity(intent);
    }
  }

  class DrawerToggle extends ActionBarDrawerToggle {

    public DrawerToggle(Activity activity, DrawerLayout drawerLayout,
                          int drawerImageRes, int openDrawerContentDescRes,
                          int closeDrawerContentDescRes) {

      super(activity, drawerLayout, drawerImageRes,
          openDrawerContentDescRes, closeDrawerContentDescRes);
    }

    @Override
    public void onDrawerClosed(View drawerView) {

      super.onDrawerClosed(drawerView);

      invalidateOptionsMenu();
    }

    @Override
    public void onDrawerOpened(View drawerView) {

      super.onDrawerOpened(drawerView);

      invalidateOptionsMenu();
    }
  }
}
