package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.DisablePushMessagingAsyncTask;
import org.thoughtcrime.securesms.util.DisablePushMessagingAsyncTask.PushDisabledCallback;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.database.EncryptedBackupExporter;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.PlaintextBackupImporter;
import org.thoughtcrime.securesms.service.ApplicationMigrationService;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.whispersystems.textsecure.storage.Session;
import org.whispersystems.textsecure.storage.SessionRecordV2;

import java.io.File;
import java.io.IOException;


public class ImportFragment extends SherlockFragment {

  private static final int SUCCESS              = 0;
  private static final int NO_SD_CARD           = 1;
  private static final int ERROR_IO             = 2;
  private static final int ERROR_BAD_PASSPHRASE = 3;

  private MasterSecret masterSecret;
  private ProgressDialog progressDialog;

  public void setMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    View layout              = inflater.inflate(R.layout.import_fragment, container, false);
    View importSmsView       = layout.findViewById(R.id.import_sms             );
    View importEncryptedView = layout.findViewById(R.id.import_encrypted_backup);
    View importPlaintextView = layout.findViewById(R.id.import_plaintext_backup);

    importSmsView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleImportSms();
      }
    });

    importEncryptedView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleImportEncryptedBackup();
      }
    });

    importPlaintextView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleImportPlaintextBackup();
      }
    });

    return layout;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    if (progressDialog != null && progressDialog.isShowing()) {
      progressDialog.dismiss();
      progressDialog = null;
    }
  }

  private void handleImportSms() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_info_icon));
    builder.setTitle(getActivity().getString(R.string.ImportFragment_import_system_sms_database));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_this_will_import_messages_from_the_system));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), new AlertDialog.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Intent intent = new Intent(getActivity(), ApplicationMigrationService.class);
        intent.setAction(ApplicationMigrationService.MIGRATE_DATABASE);
        intent.putExtra("master_secret", masterSecret);
        getActivity().startService(intent);

        Intent nextIntent = new Intent(getActivity(), ConversationListActivity.class);
        intent.putExtra("master_secret", masterSecret);

        Intent activityIntent = new Intent(getActivity(), DatabaseMigrationActivity.class);
        activityIntent.putExtra("master_secret", masterSecret);
        activityIntent.putExtra("next_intent", nextIntent);
        getActivity().startActivity(activityIntent);
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private void handleImportEncryptedBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
    builder.setTitle(getActivity().getString(R.string.ImportFragment_restore_encrypted_backup));

    final View view = LayoutInflater.from(getActivity()).inflate(R.layout.import_encrypted_dialog, null);
    final EditText passphrase = (EditText)view.findViewById(R.id.passphrase);
    builder.setView(view);
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_restore), new AlertDialog.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ImportEncryptedBackupTask(getActivity(), passphrase.getText().toString()).execute();
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private void handleImportPlaintextBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
    builder.setTitle(getActivity().getString(R.string.ImportFragment_import_plaintext_backup));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_this_will_import_messages_from_a_plaintext_backup));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), new AlertDialog.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ImportPlaintextBackupTask().execute();
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private class ImportPlaintextBackupTask extends AsyncTask<Void, Void, Integer> {

    @Override
    protected void onPreExecute() {
      progressDialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ImportFragment_importing),
                                           getActivity().getString(R.string.ImportFragment_import_plaintext_backup_elipse),
                                           true, false);
    }

    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (progressDialog != null)
        progressDialog.dismiss();

      if (context == null)
        return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_no_plaintext_backup_found),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_error_importing_backup),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_import_complete),
                         Toast.LENGTH_LONG).show();
          break;
      }
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        PlaintextBackupImporter.importPlaintextFromSd(getActivity(), masterSecret);
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ImportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        return ERROR_IO;
      }
    }
  }

  private class ImportEncryptedBackupTask extends AsyncTask<Void, Void, Integer> {
    private final Context context;
    private final String passphrase;

    public ImportEncryptedBackupTask(Context context, final String passphrase) {
      this.context    = context;
      this.passphrase = TextUtils.isEmpty(passphrase) ? MasterSecretUtil.UNENCRYPTED_PASSPHRASE : passphrase;
    }

    @Override
    protected void onPreExecute() {
      progressDialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ImportFragment_restoring),
                                           getActivity().getString(R.string.ImportFragment_restoring_encrypted_backup),
                                           true, false);
    }

    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (progressDialog != null)
        progressDialog.dismiss();

      if (context == null)
        return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_no_encrypted_backup_found),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_error_importing_backup),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_BAD_PASSPHRASE:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_error_importing_backup_passphrase),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          if (TextSecurePreferences.isPushRegistered(context)) {
            new DisablePushMessagingAsyncTask(getActivity(), new PushDisabledCallback() {
              @Override
              public void onComplete(int code) {
                Intent intent = new Intent(getActivity(), RegistrationActivity.class);
                intent.putExtra("master_secret", masterSecret);
                startActivity(intent);
              }
            }).execute();
          }

          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_restore_complete),
                         Toast.LENGTH_LONG).show();
      }
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        EncryptedBackupExporter.importFromSd(getActivity(), masterSecret, passphrase);
        Session.clearAllSessions(getActivity());
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ImportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        return ERROR_IO;
      } catch (InvalidPassphraseException e) {
        Log.w("ImportFragment", e);
        return ERROR_BAD_PASSPHRASE;
      }
    }
  }
}