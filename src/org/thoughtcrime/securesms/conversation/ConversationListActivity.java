/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.thoughtcrime.securesms.conversation;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConversationListAdapter;
import org.thoughtcrime.securesms.GroupCreateActivity;
import org.thoughtcrime.securesms.ImportExportActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.ViewLocalIdentityActivity;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener
  {
  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment fragment;
  private MasterSecret    masterSecret;
  private ContentObserver observer;

  @Override
  public void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    android.support.v7.app.ActionBar supportActionBar = getSupportActionBar();
    supportActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);

    setContentView(R.layout.conversation_list_activity);

    supportActionBar.setTitle(R.string.app_name);

    initializeResources();
    initializeContactUpdatesReceiver();

    DirectoryRefreshListener.schedule(this);
  }

  @Override
  public void onPostCreate(Bundle bundle) {
    super.onPostCreate(bundle);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onDestroy() {
    Log.w("ConversationListActivity", "onDestroy...");
    MemoryCleaner.clean(masterSecret);
    if (observer != null) getContentResolver().unregisterContentObserver(observer);
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
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    if (this.masterSecret != null) {
      inflater.inflate(R.menu.conversation_list, menu);
      MenuItem menuItem = menu.findItem(R.id.menu_search);
      initializeSearch(menuItem);
    } else {
      inflater.inflate(R.menu.conversation_list_empty, menu);
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearch(MenuItem searchViewItem) {
    SearchView searchView = (SearchView)MenuItemCompat.getActionView(searchViewItem);
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (fragment != null) {
          fragment.setQueryFilter(query);
          return true;
        }

        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
      }
    });

    MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem menuItem) {
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem menuItem) {
        if (fragment != null) {
          fragment.resetQueryFilter();
        }

        return true;
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_new_group:         createGroup();                  return true;
    case R.id.menu_settings:          handleDisplaySettings();        return true;
    case R.id.menu_clear_passphrase:  handleClearPassphrase();        return true;
    case R.id.menu_mark_all_read:     handleMarkAllRead();            return true;
    case R.id.menu_import_export:     handleImportExport();           return true;
    case R.id.menu_my_identity:       handleMyIdentity();             return true;
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

  private void createConversation(long threadId, Recipients recipients, int distributionType) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
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

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  private void handleImportExport() {
    final Intent intent = new Intent(this, ImportExportActivity.class);
    intent.putExtra("master_secret", masterSecret);
    startActivity(intent);
  }

  private void handleMyIdentity() {
    final Intent intent = new Intent(this, ViewLocalIdentityActivity.class);
    intent.putExtra("master_secret", masterSecret);
    startActivity(intent);
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
    observer = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        Log.w("ConversationListActivity", "detected android contact data changed, refreshing cache");
        // TODO only clear updated recipients from cache
        RecipientFactory.clearCache();
        ConversationListActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((ConversationListAdapter)fragment.getListAdapter()).notifyDataSetChanged();
              }
          });
      }
    };

    getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                                                 true, observer);
  }

  private void initializeResources() {
    this.masterSecret = getIntent().getParcelableExtra("master_secret");

    this.fragment = (ConversationListFragment)this.getSupportFragmentManager()
                                                  .findFragmentById(R.id.fragment_content);

    this.fragment.setMasterSecret(masterSecret);
  }
}
