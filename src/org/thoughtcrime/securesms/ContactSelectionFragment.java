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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;

/**
 * Activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 */
public class ContactSelectionFragment extends Fragment {
  private final static String TAG = "ContactSelectActivity";
  public final static String MASTER_SECRET_EXTRA = "master_secret";
  private static final int RESULT_OK = 23;

  private final DynamicTheme dynamicTheme = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();
  private MasterSecret masterSecret;

  private PushContactSelectionListFragment contactsFragment;


  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    dynamicTheme.onCreate(getActivity());
    dynamicLanguage.onCreate(getActivity());

    initializeResources();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return GUtil.setFontForFragment(getActivity(), inflater.inflate(R.layout.new_conversation_activity, container, false));
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(getActivity());
    dynamicLanguage.onResume(getActivity());
    //  getSupportActionBar().setTitle(R.string.AndroidManifest__select_contacts);
    masterSecret = getActivity().getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    ProfileAccessor.setMasterSecred(masterSecret);
  }

  private void initializeResources() {
    contactsFragment = (PushContactSelectionListFragment) getChildFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setOnContactSelectedListener(new PushContactSelectionListFragment.OnContactSelectedListener() {
      @Override
      public void onContactSelected(ContactData contactData) {
        Log.i(TAG, "Choosing contact from list.");
        Recipients recipients = contactDataToRecipients(contactData);
        openNewConversation(recipients);
      }
    });
  }

  private void handleSelectionFinished() {
    final Intent resultIntent = getActivity().getIntent();
    final List<ContactData> selectedContacts = contactsFragment.getSelectedContacts();
    if (selectedContacts != null) {
      resultIntent.putParcelableArrayListExtra("contacts", new ArrayList<ContactData>(contactsFragment.getSelectedContacts()));
    }
    getActivity().setResult(RESULT_OK, resultIntent);
    //finish();
  }

  private void handleDirectoryRefresh() {
    DirectoryHelper.refreshDirectoryWithProgressDialog(getActivity(), new DirectoryHelper.DirectoryUpdateFinishedListener() {
      @Override
      public void onUpdateFinished() {
        contactsFragment.update();
      }
    });
  }

  private Recipients contactDataToRecipients(ContactData contactData) {
    if (contactData == null || contactData.numbers == null) return null;
    Recipients recipients = new Recipients(new LinkedList<Recipient>());
    for (ContactAccessor.NumberData numberData : contactData.numbers) {
      if (NumberUtil.isValidSmsOrEmailOrGroup(numberData.number)) {

        Recipients recipientsForNumber = RecipientFactory.getRecipientsFromString(getActivity(),
            numberData.number,
            false);
        recipients.getRecipientsList().addAll(recipientsForNumber.getRecipientsList());

      }
    }
    return recipients;
  }

  private void openNewConversation(Recipients recipients) {
    if (recipients != null) {
      Intent intent = new Intent(getActivity(), ConversationActivity.class);
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
      intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
      intent.putExtra(ConversationActivity.DRAFT_TEXT_EXTRA, getActivity().getIntent().getStringExtra(ConversationActivity.DRAFT_TEXT_EXTRA));
      intent.putExtra(ConversationActivity.DRAFT_AUDIO_EXTRA, getActivity().getIntent().getParcelableExtra(ConversationActivity.DRAFT_AUDIO_EXTRA));
      intent.putExtra(ConversationActivity.DRAFT_VIDEO_EXTRA, getActivity().getIntent().getParcelableExtra(ConversationActivity.DRAFT_VIDEO_EXTRA));
      intent.putExtra(ConversationActivity.DRAFT_IMAGE_EXTRA, getActivity().getIntent().getParcelableExtra(ConversationActivity.DRAFT_IMAGE_EXTRA));
      long existingThread = DatabaseFactory.getThreadDatabase(getActivity()).getThreadIdIfExistsFor(recipients);
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
      intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
      startActivity(intent);
    }
  }

  public static ContactSelectionFragment newInstance(String title) {
    ContactSelectionFragment fragmentFirst = new ContactSelectionFragment();
    Bundle args = new Bundle();
    args.putString(ConversationListActivity.PagerAdapter.EXTRA_FRAGMENT_PAGE_TITLE, title);
    fragmentFirst.setArguments(args);
    return fragmentFirst;
  }

  public void reloadAdapter() {
    if (isAdded()) {
      contactsFragment.getLoaderManager().restartLoader(0, null, contactsFragment);
    }
  }

}
