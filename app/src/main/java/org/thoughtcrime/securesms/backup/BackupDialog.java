package org.thoughtcrime.securesms.backup;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.registration.fragments.RestoreBackupFragment;
import org.thoughtcrime.securesms.service.LocalBackupListener;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

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
          BackupPassphrase.set(context, Util.join(password, " "));
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
                     BackupPassphrase.set(context, null);
                     TextSecurePreferences.setBackupEnabled(context, false);
                     BackupUtil.deleteAllBackups();
                     preference.setChecked(false);
                   })
                   .create()
                   .show();
  }

  public static void showVerifyBackupPassphraseDialog(@NonNull Context context) {
    View        view   = LayoutInflater.from(context).inflate(R.layout.enter_backup_passphrase_dialog, null);
    EditText    prompt = view.findViewById(R.id.restore_passphrase_input);
    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.BackupDialog_enter_backup_passphrase_to_verify)
                                        .setView(view)
                                        .setPositiveButton(R.string.BackupDialog_verify, null)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show();

    Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    positiveButton.setEnabled(false);

    RestoreBackupFragment.PassphraseAsYouTypeFormatter formatter = new RestoreBackupFragment.PassphraseAsYouTypeFormatter();

    prompt.addTextChangedListener(new AfterTextChanged(editable -> {
        formatter.afterTextChanged(editable);
        positiveButton.setEnabled(editable.length() == BackupUtil.PASSPHRASE_LENGTH);
      }));

    positiveButton.setOnClickListener(v -> {
                                        String passphrase = prompt.getText().toString();
                                        if (passphrase.equals(BackupPassphrase.get(context))) {
                                          Toast.makeText(context, R.string.BackupDialog_you_successfully_entered_your_backup_passphrase, Toast.LENGTH_SHORT).show();
                                          dialog.dismiss();
                                        } else {
                                          Toast.makeText(context, R.string.BackupDialog_passphrase_was_not_correct, Toast.LENGTH_SHORT).show();
                                        }
                                      });
  }
}
