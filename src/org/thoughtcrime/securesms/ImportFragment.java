package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.app.IntentService;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptedBackupExporter;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.PlaintextBackupImporter;
import org.thoughtcrime.securesms.service.ApplicationMigrationService;
import org.thoughtcrime.securesms.service.KeyCachingService;

import java.io.IOException;


public class ImportFragment extends Fragment {

  private static final String IMPORTPLAINTEXT_FILTER = "ImportFragment_importPlaintextReceiver";
  private static final String IMPORTENCRYPTED_FILTER = "ImportFragment_importEncryptedReceiver";
  private static final int SUCCESS    = 0;
  private static final int NO_SD_CARD = 1;
  private static final int ERROR_IO   = 2;

  private MasterSecret masterSecret;
  private ProgressDialog progressDialog;
  private ImportPlaintextBackupReceiver importPlaintextReceiver;
  private ImportEncryptedBackupReceiver importEncryptedReceiver;

  public void setMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  @Override
  public void onAttach(android.app.Activity activity) {
    super.onAttach(activity);
    importPlaintextReceiver = new ImportPlaintextBackupReceiver();
    LocalBroadcastManager.getInstance(activity)
      .registerReceiver(importPlaintextReceiver, new IntentFilter(IMPORTPLAINTEXT_FILTER));
    importEncryptedReceiver = new ImportEncryptedBackupReceiver();
    LocalBroadcastManager.getInstance(activity)
      .registerReceiver(importEncryptedReceiver, new IntentFilter(IMPORTENCRYPTED_FILTER));
  }

  @Override
  public void onDetach() {
    super.onDetach();
    if (importPlaintextReceiver != null)
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(importPlaintextReceiver);
    if (importEncryptedReceiver != null)
      LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(importEncryptedReceiver);
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
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_info_icon);
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

        Intent activityIntent = new Intent(getActivity(), DatabaseMigrationActivity.class);
        activityIntent.putExtra("next_intent", nextIntent);
        getActivity().startActivity(activityIntent);
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private void handleImportEncryptedBackup() {
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ImportFragment_restore_encrypted_backup));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_restoring_an_encrypted_backup_will_completely_replace_your_existing_keys));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_restore), new AlertDialog.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Intent importEncrypted = new Intent(getActivity(), ImportEncryptedBackupService.class);
        importEncryptedReceiver.showProgressDialog();
        getActivity().startService(importEncrypted);
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private void handleImportPlaintextBackup() {
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ImportFragment_import_plaintext_backup));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_this_will_import_messages_from_a_plaintext_backup));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), new AlertDialog.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        Intent importPlaintext = new Intent(getActivity(), ImportPlaintextBackupService.class);
        importPlaintextReceiver.showProgressDialog();
        importPlaintext.putExtra("masterSecret", masterSecret);
        getActivity().startService(importPlaintext);
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private class ImportPlaintextBackupReceiver extends BroadcastReceiver {

    public void showProgressDialog() {
      progressDialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ImportFragment_importing),
                                           getActivity().getString(R.string.ImportFragment_import_plaintext_backup_elipse),
                                           true, false);
    }

    @Override
    public void onReceive(Context receiverContext, Intent receiverIntent) {
      int result = receiverIntent.getIntExtra("returnValue", SUCCESS);
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
  }

  public static class ImportPlaintextBackupService extends IntentService {
    private MasterSecret masterSecret;

    public ImportPlaintextBackupService() {
      super("ImportPlaintextBackupService");
    }

    public void onHandleIntent(Intent intent) {
      this.masterSecret = (MasterSecret) intent.getParcelableExtra("masterSecret");
      Intent resultIntent = new Intent(IMPORTPLAINTEXT_FILTER);
      try {
        PlaintextBackupImporter.importPlaintextFromSd(this, masterSecret);
        resultIntent.putExtra("returnValue", SUCCESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      } catch (NoExternalStorageException e) {
        Log.w("ImportFragment", e);
        resultIntent.putExtra("returnValue", NO_SD_CARD);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        resultIntent.putExtra("returnValue", ERROR_IO);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      }
    }
  }

  private class ImportEncryptedBackupReceiver extends BroadcastReceiver {

    public void showProgressDialog() {
      progressDialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ImportFragment_restoring),
                                           getActivity().getString(R.string.ImportFragment_restoring_encrypted_backup),
                                           true, false);
    }

    @Override
    public void onReceive(Context receiverContext, Intent receiverIntent) {
      int result = receiverIntent.getIntExtra("returnValue", SUCCESS);
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
        case SUCCESS:
          DatabaseFactory.getInstance(context).reset(context);
          Intent intent = new Intent(context, KeyCachingService.class);
          intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
          context.startService(intent);

          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_restore_complete),
                         Toast.LENGTH_LONG).show();
      }
    }
  }

  public static class ImportEncryptedBackupService extends IntentService {

    public ImportEncryptedBackupService() {
      super("ImportEncryptedBackupService");
    }

    public void onHandleIntent(Intent intent) {
      Intent resultIntent = new Intent(IMPORTENCRYPTED_FILTER);
      try {
        EncryptedBackupExporter.importFromSd(this);
        resultIntent.putExtra("returnValue", SUCCESS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      } catch (NoExternalStorageException e) {
        Log.w("ImportFragment", e);
        resultIntent.putExtra("returnValue", NO_SD_CARD);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        resultIntent.putExtra("returnValue", ERROR_IO);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
      }
    }
  }
}