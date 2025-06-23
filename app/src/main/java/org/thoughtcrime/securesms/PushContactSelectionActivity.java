/*
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

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public class PushContactSelectionActivity extends ContactSelectionActivity {

  public static final String KEY_SELECTED_RECIPIENTS = "recipients";

  @SuppressWarnings("unused")
  private final static String TAG = Log.tag(PushContactSelectionActivity.class);

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    super.onCreate(icicle, ready);

    initializeToolbar();
  }

  protected void initializeToolbar() {
    getToolbar().setNavigationIcon(R.drawable.ic_check_24);
    getToolbar().setNavigationOnClickListener(v -> {
      onFinishedSelection();
    });
  }

  protected final void onFinishedSelection() {
    Intent                resultIntent     = getIntent();
    List<SelectedContact> selectedContacts = contactsFragment.getSelectedContacts();
    List<RecipientId>     recipients       = Stream.of(selectedContacts).map(sc -> sc.getOrCreateRecipientId()).toList();

    resultIntent.putParcelableArrayListExtra(KEY_SELECTED_RECIPIENTS, new ArrayList<>(recipients));

    setResult(RESULT_OK, resultIntent);
    finish();
  }

  @Override
  public void onSelectionChanged() {
  }
}
