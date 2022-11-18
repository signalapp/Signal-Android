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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.view.ViewCompat;

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
public class PushContactSelectionActivity extends ContactSelectionActivity implements ContactSelectionListFragment.ApplyCallBack,ContactSelectionListFragment.SearchCallBack{

  public static final String KEY_SELECTED_RECIPIENTS = "recipients";

  @SuppressWarnings("unused")
  private final static String TAG = Log.tag(PushContactSelectionActivity.class);

  private static final float WELCOME_OPTIOON_SCALE_FOCUS = 1.3f;
  private static final float WELCOME_OPTIOON_SCALE_NON_FOCUS = 1.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_FOCUS = 12.0f;
  private static final float WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS = 1.0f;

  private void MP02_Animate(View view, boolean b) {
    float scale = b ? WELCOME_OPTIOON_SCALE_FOCUS : WELCOME_OPTIOON_SCALE_NON_FOCUS;
    float transx = b ? WELCOME_OPTIOON_TRANSLATION_X_FOCUS : WELCOME_OPTIOON_TRANSLATION_X_NON_FOCUS;
    ViewCompat.animate(view)
            .scaleX(scale)
            .scaleY(scale)
            .translationX(transx)
            .start();
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    super.onCreate(icicle, ready);

    initializeToolbar();
  }

  @Override
  public void onSearch(View view) {

  }

  @Override
  public void onApply() {
    Intent resultIntent = getIntent();
    List<SelectedContact> selectedContacts = contactsFragment.getSelectedContacts();
    List<RecipientId> recipients = Stream.of(selectedContacts).map(sc -> sc.getOrCreateRecipientId(this)).toList();

    resultIntent.putParcelableArrayListExtra(KEY_SELECTED_RECIPIENTS, new ArrayList<>(recipients));

    setResult(RESULT_OK, resultIntent);
    finish();
  }

  protected void initializeToolbar() {
    getConfirmTextView().setVisibility(View.GONE);
    getConfirmTextView().setOnFocusChangeListener(this::MP02_Animate);
  }

  protected final void onFinishedSelection() {
    Intent                resultIntent     = getIntent();
    List<SelectedContact> selectedContacts = contactsFragment.getSelectedContacts();
    List<RecipientId>     recipients       = Stream.of(selectedContacts).map(sc -> sc.getOrCreateRecipientId(this)).toList();

    resultIntent.putParcelableArrayListExtra(KEY_SELECTED_RECIPIENTS, new ArrayList<>(recipients));

    setResult(RESULT_OK, resultIntent);
    finish();
  }

  @Override
  public void onSelectionChanged() {

  }
}
