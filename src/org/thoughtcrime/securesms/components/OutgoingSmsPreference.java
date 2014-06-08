/**
 * Copyright (C) 2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class OutgoingSmsPreference extends DialogPreference {
  private CheckBox dataUsers;
  private CheckBox askForFallback;
  private CheckBox nonDataUsers;
  public OutgoingSmsPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    setPersistent(false);
    setDialogLayoutResource(R.layout.outgoing_sms_preference);
  }

  @Override
  protected void onBindDialogView(final View view) {
    super.onBindDialogView(view);
    dataUsers      = (CheckBox) view.findViewById(R.id.data_users);
    askForFallback = (CheckBox) view.findViewById(R.id.ask_before_fallback_data);
    nonDataUsers   = (CheckBox) view.findViewById(R.id.non_data_users);

    dataUsers.setChecked(TextSecurePreferences.isSmsFallbackEnabled(getContext()));
    askForFallback.setChecked(TextSecurePreferences.isSmsFallbackAskEnabled(getContext()));
    nonDataUsers.setChecked(TextSecurePreferences.isSmsNonDataOutEnabled(getContext()));

    dataUsers.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        askForFallback.setEnabled(dataUsers.isChecked());
      }
    });

    askForFallback.setEnabled(dataUsers.isChecked());
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);

    if (positiveResult) {
      TextSecurePreferences.setSmsFallbackEnabled(getContext(), dataUsers.isChecked());
      TextSecurePreferences.setSmsFallbackAskEnabled(getContext(), askForFallback.isChecked());
      TextSecurePreferences.setSmsNonDataOutEnabled(getContext(), nonDataUsers.isChecked());
      if (getOnPreferenceChangeListener() != null) getOnPreferenceChangeListener().onPreferenceChange(this, null);
    }
  }
}
