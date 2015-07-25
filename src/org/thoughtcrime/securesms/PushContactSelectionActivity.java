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
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public class PushContactSelectionActivity extends ContactSelectionActivity {

  private final static String TAG = PushContactSelectionActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    super.onCreate(icicle, masterSecret);
    contactsFragment.setMultiSelect(true);

    action.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_white_24dp));
    action.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent       resultIntent     = getIntent();
        List<String> selectedContacts = contactsFragment.getSelectedContacts();

        if (selectedContacts != null) {
          resultIntent.putStringArrayListExtra("contacts", new ArrayList<>(selectedContacts));
        }

        setResult(RESULT_OK, resultIntent);
        finish();
      }
    });
  }
}
