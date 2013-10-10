/**
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
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.text.InputType;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NotificationsDatabase;

/**
 * List preference for Vibrate pattern notification.
 * 
 */

public class VibratePatternListPreference extends ListPreference {

  private Context context;
  private boolean dialogInProgress;
  private EditText editText;
  private long rowId;

  public VibratePatternListPreference(Context context) {
    super(context);
    this.context = context;
  }

  public VibratePatternListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  public void setRowId(long rowId) {
    this.rowId = rowId;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);

    if (positiveResult) {
      String vibratePattern = this.getValue();

      if (vibratePattern.equals("custom"))
        showDialog();
    }
  }

  private void initializeDialog(View view) {
    Cursor c = DatabaseFactory.getNotificationsDatabase(context).getDefaultNotification();
    String vibratePattern =
        c.getString(c.getColumnIndexOrThrow(NotificationsDatabase.VIBRATE_PATTERN_CUSTOM));
    c.close();

    editText = (EditText) view.findViewById(R.id.CustomVibrateEditText);
    editText.setText(vibratePattern);
    editText.setKeyListener(new NumberKeyListener() {
      @Override
      public int getInputType() {
        return InputType.TYPE_MASK_VARIATION;
      }

      @Override
      protected char[] getAcceptedChars() {
        return new char[] { ',', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' };
      }
    });

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setTitle(context.getResources().getString(
        R.string.preferences__vibrate_pattern_custom_title));
    builder.setView(view);
    builder.setOnCancelListener(new CustomDialogCancelListener());
    builder.setNegativeButton(android.R.string.cancel, new CustomDialogCancelListener());
    builder.setPositiveButton(android.R.string.ok, new CustomDialogClickListener());
    builder.show();
  }

  private void showDialog() {
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View view = inflater.inflate(R.layout.vibrate_pattern_dialog, null);

    initializeDialog(view);
    dialogInProgress = true;
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

  private class CustomDialogCancelListener implements DialogInterface.OnClickListener,
      DialogInterface.OnCancelListener {
    public void onClick(DialogInterface dialog, int which) {
      dialogInProgress = false;
    }

    public void onCancel(DialogInterface dialog) {
      dialogInProgress = false;
    }
  }

  private class CustomDialogClickListener implements DialogInterface.OnClickListener {

    public void onClick(DialogInterface dialog, int which) {
      String pattern = editText.getText().toString();
      dialogInProgress = false;

      DatabaseFactory.getNotificationsDatabase(context).updateNotification(rowId,
          NotificationsDatabase.VIBRATE_PATTERN_CUSTOM, pattern);

      Toast.makeText(context,
          context.getResources().getString(R.string.preferences__vibrate_pattern_set),
          Toast.LENGTH_LONG).show();
    }

  }

}
