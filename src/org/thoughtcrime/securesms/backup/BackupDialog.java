package org.thoughtcrime.securesms.backup;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

public class BackupDialog {

  public static void showEnableBackupDialog(@NonNull Context context, @NonNull SwitchPreferenceCompat preference) {
    String[]    password = BackupUtil.generateBackupPassphrase();
    AlertDialog dialog   = new AlertDialog.Builder(context)
                                          .setTitle(R.string.BackupDialog_enable_local_backups)
                                          .setView(R.layout.backup_enable_dialog)
                                          .setPositiveButton(R.string.BackupDialog_enable_backups, null)
                                          .setNegativeButton(android.R.string.cancel, null)
                                          .create();

    dialog.setOnShowListener(created -> {
      Button button = ((AlertDialog) created).getButton(AlertDialog.BUTTON_POSITIVE);
      button.setOnClickListener(v -> {
        CheckBox confirmationCheckBox = dialog.findViewById(R.id.confirmation_check);
        if (confirmationCheckBox.isChecked()) {
          TextSecurePreferences.setBackupPassphrase(context, Util.join(password, " "));
          TextSecurePreferences.setBackupEnabled(context, true);
          LocalBackupListener.schedule(context);

          preference.setChecked(true);
          created.dismiss();
        } else {
          Toast.makeText(context, R.string.BackupDialog_please_acknowledge_your_understanding_by_marking_the_confirmation_check_box, Toast.LENGTH_LONG).show();
        }
      });
    });

    dialog.show();

    CheckBox checkBox = dialog.findViewById(R.id.confirmation_check);
    TextView textView = dialog.findViewById(R.id.confirmation_text);

    ((TextView)dialog.findViewById(R.id.code_first)).setText(password[0]);
    ((TextView)dialog.findViewById(R.id.code_second)).setText(password[1]);
    ((TextView)dialog.findViewById(R.id.code_third)).setText(password[2]);

    ((TextView)dialog.findViewById(R.id.code_fourth)).setText(password[3]);
    ((TextView)dialog.findViewById(R.id.code_fifth)).setText(password[4]);
    ((TextView)dialog.findViewById(R.id.code_sixth)).setText(password[5]);

    textView.setOnClickListener(v -> checkBox.toggle());

    dialog.findViewById(R.id.number_table).setOnClickListener(v -> {
      ((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("text", Util.join(password, " ")));
      Toast.makeText(context, R.string.BackupDialog_copied_to_clipboard, Toast.LENGTH_LONG).show();
    });


  }

  public static void showDisableBackupDialog(@NonNull Context context, @NonNull SwitchPreferenceCompat preference) {
    new AlertDialog.Builder(context)
                   .setTitle(R.string.BackupDialog_delete_backups)
                   .setMessage(R.string.BackupDialog_disable_and_delete_all_local_backups)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(R.string.BackupDialog_delete_backups_statement, (dialog, which) -> {
                     TextSecurePreferences.setBackupPassphrase(context, null);
                     TextSecurePreferences.setBackupEnabled(context, false);
                     BackupUtil.deleteAllBackups();
                     preference.setChecked(false);
                   })
                   .create()
                   .show();
  }
}
