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
package org.thoughtcrime.securesms.preferences;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;

import android.content.Context;
import android.content.DialogInterface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Dialog preference for encryption passphrase timeout.
 * 
 * @author Moxie Marlinspike
 */

public class PassphraseTimeoutPreference extends DialogPreference {

  private Spinner scaleSpinner;
  private SeekBar seekBar;
  private TextView timeoutText;
	
  public PassphraseTimeoutPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.setDialogLayoutResource(R.layout.passphrase_timeout_dialog);
    this.setPositiveButtonText("Ok");
    this.setNegativeButtonText("Cancel");		
  }
	
  @Override
  protected View onCreateDialogView() {
    View dialog       = super.onCreateDialogView();
    this.scaleSpinner = (Spinner)dialog.findViewById(R.id.scale);
    this.seekBar      = (SeekBar)dialog.findViewById(R.id.seekbar);
    this.timeoutText  = (TextView)dialog.findViewById(R.id.timeout_text);

    initializeDefaults();
    initializeListeners();
		
    return dialog;
  }
	
  @Override
  public void onClick(DialogInterface dialog, int which) {
    if (which == DialogInterface.BUTTON_POSITIVE) {
      int interval;
			
      if (scaleSpinner.getSelectedItemPosition() == 0) {
        interval = Math.max(seekBar.getProgress(), 1);
      } else {
        interval = Math.max(seekBar.getProgress(), 1) * 60;
      }
				
      this.getSharedPreferences().edit().putInt(ApplicationPreferencesActivity.PASSPHRASE_TIMEOUT_INTERVAL_PREF, interval).commit();
    }
		
    super.onClick(dialog, which);
  }
	
  private void initializeDefaults() {
    int timeout = this.getSharedPreferences().getInt(ApplicationPreferencesActivity.PASSPHRASE_TIMEOUT_INTERVAL_PREF, 60 * 5);
		
    if (timeout > 60) {
      scaleSpinner.setSelection(1);
      seekBar.setMax(24);
      seekBar.setProgress(timeout / 60);
      timeoutText.setText((timeout / 60) + "");
    } else {
      scaleSpinner.setSelection(0);
      seekBar.setMax(60);
      seekBar.setProgress(timeout);
      timeoutText.setText(timeout + "");
    }
  }

  private void initializeListeners() {
    this.seekBar.setOnSeekBarChangeListener(new SeekbarChangedListener());
    this.scaleSpinner.setOnItemSelectedListener(new ScaleSelectedListener());		
  }
	
  private class ScaleSelectedListener implements AdapterView.OnItemSelectedListener {
    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long selected) {
      if (selected == 0) {
        seekBar.setMax(60);
      } else {
        seekBar.setMax(24);
      }
    }

    public void onNothingSelected(AdapterView<?> arg0) {
    }		
  }

  private class SeekbarChangedListener implements SeekBar.OnSeekBarChangeListener {

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      if (progress < 1) 
        progress = 1;
			
      timeoutText.setText(progress +"");
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
    }
		
  }
	
}
