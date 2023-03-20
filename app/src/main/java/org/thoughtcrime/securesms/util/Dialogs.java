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
package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

public class Dialogs {
  public static void showAlertDialog(Context context, String title, String message) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  public static void showInfoDialog(Context context, String title, String message) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setIcon(R.drawable.symbol_info_24)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  public static void showFormattedTextDialog(@NonNull Context context, @NonNull Runnable onSendAnyway) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(R.string.SendingFormattingTextDialog_title)
        .setMessage(R.string.SendingFormattingTextDialog_message)
        .setNegativeButton(R.string.SendingFormattingTextDialog_cancel_send_button, null)
        .setPositiveButton(R.string.SendingFormattingTextDialog_send_anyway_button, (d, w) -> {
          SignalStore.uiHints().markHasSeenTextFormattingAlert();
          onSendAnyway.run();
        })
        .show();
  }
}
