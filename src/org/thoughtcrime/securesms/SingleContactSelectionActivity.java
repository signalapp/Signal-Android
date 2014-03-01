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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.components.SingleRecipientPanel;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.util.ArrayList;
import java.util.List;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;

/**
 * Activity container for selecting a list of contacts.  Provides a tab frame for
 * contact, group, and "recent contact" activity tabs.  Used by ComposeMessageActivity
 * when selecting a list of contacts to address a message to.
 *
 * @author Moxie Marlinspike
 *
 */
public class SingleContactSelectionActivity extends PassphraseRequiredSherlockFragmentActivity {
  private final static String TAG                 = "SingleContactSelectionActivity";
  public final static  String MASTER_SECRET_EXTRA = "master_secret";

  private final DynamicTheme dynamicTheme = new DynamicTheme();
  private MasterSecret masterSecret;
  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    super.onCreate(icicle);

    final ActionBar actionBar = this.getSupportActionBar();
    ActionBarUtil.initializeDefaultActionBar(this, actionBar);
    actionBar.setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.single_contact_selection_activity);
    initializeResources();
  }

  private void initializeResources() {
    masterSecret = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    final SingleRecipientPanel recipientsPanel = (SingleRecipientPanel) findViewById(R.id.recipients);

    final SingleContactSelectionListFragment listFragment = (SingleContactSelectionListFragment)getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);

    listFragment.setOnContactSelectedListener(new SingleContactSelectionListFragment.OnContactSelectedListener() {
      @Override
      public void onContactSelected(ContactData contactData) {
        Log.i(TAG, "Choosing contact from list.");
        Recipients recipients = contactDataToRecipients(contactData);
        openNewConversation(recipients);
      }
    });

    recipientsPanel.setPanelChangeListener(new SingleRecipientPanel.RecipientsPanelChangedListener() {
      @Override
      public void onRecipientsPanelUpdate(Recipients recipients) {
        Log.i(TAG, "Choosing contact from autocompletion.");
        openNewConversation(recipients);
      }
    });
  }

  private Recipients contactDataToRecipients(ContactData contactData) {
    if (contactData == null || contactData.numbers == null) return null;
    Recipients recipients = new Recipients(new ArrayList<Recipient>());
    for (ContactAccessor.NumberData numberData : contactData.numbers) {
      if (NumberUtil.isValidSmsOrEmailOrGroup(numberData.number)) {
        try {
          Recipients recipientsForNumber = RecipientFactory.getRecipientsFromString(SingleContactSelectionActivity.this,
              numberData.number,
              false);
          recipients.getRecipientsList().addAll(recipientsForNumber.getRecipientsList());
        } catch (RecipientFormattingException rfe) {
          Log.w(TAG, "Caught RecipientFormattingException when trying to convert a selected number to a Recipient.", rfe);
        }
      }
    }
    return recipients;
  }

  private void openNewConversation(Recipients recipients) {
    if (recipients != null) {
      Intent intent = new Intent(SingleContactSelectionActivity.this, ConversationActivity.class);
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.toIdString());
      intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
      long existingThread = DatabaseFactory.getThreadDatabase(SingleContactSelectionActivity.this).getThreadIdIfExistsFor(recipients);
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
      intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
      startActivity(intent);
      finish();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    if (TextSecurePreferences.isPushRegistered(this)) {
      inflater.inflate(R.menu.push_directory, menu);
    }

    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_refresh_directory:
      handleDirectoryRefresh();
      return true;
    case android.R.id.home:
      setResult(RESULT_CANCELED);
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void handleDirectoryRefresh() {
    if (!TextSecurePreferences.isPushRegistered(SingleContactSelectionActivity.this)) {
      Toast.makeText(getApplicationContext(),
              getString(R.string.SingleContactSelectionActivity_you_are_not_registered_with_the_push_service),
              Toast.LENGTH_LONG).show();
      return;
    }

    new AsyncTask<Void, Void, Void>() {
      private ProgressDialog progress;

      @Override
      protected void onPreExecute() {
        progress = ProgressDialog.show(SingleContactSelectionActivity.this,
                getString(R.string.SingleContactSelectionActivity_updating_directory),
                getString(R.string.SingleContactSelectionActivity_updating_push_directory),
                true);
      }

      @Override
      protected Void doInBackground(Void... params) {
        DirectoryHelper.refreshDirectory(getApplicationContext());
        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        final SingleContactSelectionListFragment listFragment = (SingleContactSelectionListFragment)getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
        listFragment.update();
        if (progress != null)
          progress.dismiss();
      }
    }.execute();

  }
}
