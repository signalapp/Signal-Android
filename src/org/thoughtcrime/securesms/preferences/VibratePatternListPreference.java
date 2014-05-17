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

import android.app.AlertDialog;
import android.os.Vibrator;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Dialogs;

/**
 * List preference for Vibrate Pattern
 *
 * @author agrajaghh
 */

public class VibratePatternListPreference extends ListPreference  {

  private Context context;

  private EditText vibratePatternEditText;

  private boolean dialogInProgress;

  public VibratePatternListPreference(Context context) {
    super(context);
    this.context = context;
  }

  public VibratePatternListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);

    if (positiveResult && getValue().equals("custom")) {
      showDialog();
    }
  }

    private void showDialog() {
    LayoutInflater inflater     = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View view                   = inflater.inflate(R.layout.vibrate_pattern_dialog, null);
    this.vibratePatternEditText = (EditText)view.findViewById(R.id.editTextPattern);

    initializeDialog(view);
    vibratePatternEditText.setText(TextSecurePreferences.getNotificationVibratePatternCustom(context));
    dialogInProgress = true;
  }

  private void initializeDialog(View view) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setIcon(Dialogs.resolveIcon(context, R.attr.dialog_info_icon));
    builder.setTitle(R.string.preferences__pref_vibrate_custom_pattern_title);
    builder.setView(view);
    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      public void onCancel (DialogInterface dialog){
        dialogInProgress = false;
      }
    });
    builder.setPositiveButton(android.R.string.ok, new EmptyClickListener());
    builder.setNeutralButton(R.string.preferences__pref_vibrate_custom_pattern_test, new EmptyClickListener());
    builder.setNegativeButton(android.R.string.cancel, new EmptyClickListener());
    builder.setInverseBackgroundForced(true);

    AlertDialog dialog = builder.show();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new OkayListener(dialog));
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new TestListener(context));
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new CancelListener(dialog));
  }

  private class EmptyClickListener implements DialogInterface.OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) { }
  }

  private class OkayListener implements View.OnClickListener {
    private AlertDialog dialog;
    public OkayListener( AlertDialog dialog) { this.dialog = dialog; }

    @Override
    public void onClick(View view) {
      final String VibratePattern = vibratePatternEditText.getText().toString();

      try
      {
        MessageNotifier.parseVibratePattern(VibratePattern);
        TextSecurePreferences.setNotificationVibratePatternCustom(context, VibratePattern);
        Toast.makeText(context, R.string.preferences__pref_vibrate_custom_pattern_set, Toast.LENGTH_LONG).show();
        dialog.dismiss();
        dialogInProgress = false;
      }
      catch(NumberFormatException e)
      {
        Toast.makeText(context, R.string.preferences__pref_vibrate_custom_pattern_wrong, Toast.LENGTH_LONG).show();
      }
    }
  }

  private class TestListener implements View.OnClickListener {
    private Context context;
    public TestListener(Context context) { this.context = context; }

    @Override
    public void onClick(View view) {
      final String VibratePattern = vibratePatternEditText.getText().toString();

      try
      {
        Vibrator vibrator = (Vibrator)this.context.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(MessageNotifier.parseVibratePattern(VibratePattern), -1);
      }
      catch(NumberFormatException e)
      {
        Toast.makeText(context, R.string.preferences__pref_vibrate_custom_pattern_wrong, Toast.LENGTH_LONG).show();
      }
    }
  }

  private class CancelListener implements View.OnClickListener {
    private AlertDialog dialog;
    public CancelListener(AlertDialog dialog) { this.dialog = dialog; }

    @Override
    public void onClick(View view) {
      dialog.dismiss();
      dialogInProgress = false;
    }
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    super.onRestoreInstanceState(state);
    if (dialogInProgress) {
      showDialog();
    }
  }

  @Override
  protected View onCreateDialogView() {
    dialogInProgress = false;
    return super.onCreateDialogView();
  }
}