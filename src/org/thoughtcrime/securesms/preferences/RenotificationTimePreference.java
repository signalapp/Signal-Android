/**
 * Copyright (C) 2014 Whisper Systems
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

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Dialog preference for the time interval between renotifications
 *
 * @author Lukas Barth
 */

public class RenotificationTimePreference extends DialogPreference {

  private Spinner scaleSpinner;
  private SeekBar seekBar;
  private TextView timeText;

  public RenotificationTimePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.setDialogLayoutResource(R.layout.renotification_time_dialog);
    this.setPositiveButtonText(android.R.string.ok);
    this.setNegativeButtonText(android.R.string.cancel);
  }

  private void updateText() {
    int time = seekBar.getProgress();

    if (time > 0) {
      timeText.setText(time + "");
      scaleSpinner.setVisibility(View.VISIBLE);
    } else {
      timeText.setText(R.string.preferences__disabled);
      scaleSpinner.setVisibility(View.GONE);
    }
  }

  @Override
  protected View onCreateDialogView() {
    View dialog       = super.onCreateDialogView();
    this.scaleSpinner = (Spinner)dialog.findViewById(R.id.scale);
    this.seekBar      = (SeekBar)dialog.findViewById(R.id.seekbar);
    this.timeText = (TextView)dialog.findViewById(R.id.time_text);

    // Can't figure out how to style a DialogPreference.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      this.timeText.setTextColor(Color.parseColor("#cccccc"));
    }

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

      TextSecurePreferences.setRenotificationTime(getContext(), interval);
    }

    super.onClick(dialog, which);
  }

  private void initializeDefaults() {
    int time = TextSecurePreferences.getRenotificationTime(getContext());

    if (time > 60) {
      scaleSpinner.setSelection(1);
      seekBar.setMax(24);
      seekBar.setProgress(time / 60);
    } else {
      scaleSpinner.setSelection(0);
      seekBar.setMax(60);
      seekBar.setProgress(time);
    }

    this.updateText();
  }

  private void initializeListeners() {
    this.seekBar.setOnSeekBarChangeListener(new SeekbarChangedListener());
    this.scaleSpinner.setOnItemSelectedListener(new ScaleSelectedListener());
  }

  private class ScaleSelectedListener implements AdapterView.OnItemSelectedListener {
    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long selected) {
      if (selected == 0) {
        seekBar.setMax(60);
      } else {
        seekBar.setMax(24);
      }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }
  }

  private class SeekbarChangedListener implements SeekBar.OnSeekBarChangeListener {

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      RenotificationTimePreference.this.updateText();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

  }

}
