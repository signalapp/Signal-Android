package org.thoughtcrime.securesms;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.PlaintextBackupExporter;
import org.thoughtcrime.securesms.database.PlaintextBackupImporter;
import org.thoughtcrime.securesms.service.ApplicationMigrationService;

import java.io.FileNotFoundException;
import java.io.IOException;


public class ImportExportFragment extends Fragment {

  private static final int SUCCESS        = 0;
  private static final int NO_SD_CARD     = 1;
  private static final int ERROR_IO       = 2;
  private static final int FILE_NOT_FOUND = 3;

  private MasterSecret   masterSecret;
  private ProgressDialog progressDialog;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    View layout              = inflater.inflate(R.layout.import_export_fragment, container, false);
    View importSmsView       = layout.findViewById(R.id.import_sms             );
    View importPlaintextView = layout.findViewById(R.id.import_plaintext_backup);
    View exportPlaintextView = layout.findViewById(R.id.export_plaintext_backup);

    importSmsView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleImportSms();
      }
    });

    importPlaintextView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleImportPlaintextBackup();
      }
    });

    exportPlaintextView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleExportPlaintextBackup();
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

  private void handleImportPlaintextBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
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

  private void handleExportPlaintextBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ExportFragment_export_plaintext_to_storage));
    builder.setMessage(getActivity().getString(R.string.ExportFragment_warning_this_will_export_the_plaintext_contents));
    builder.setPositiveButton(getActivity().getString(R.string.ExportFragment_export), new Dialog.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ExportPlaintextTask().execute();
      }
    });
    builder.setNegativeButton(getActivity().getString(R.string.ExportFragment_cancel), null);
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
        case FILE_NOT_FOUND:
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
      } catch (FileNotFoundException e) {
        Log.w("ImportFragment", e);
        return FILE_NOT_FOUND;
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        return ERROR_IO;
      }
    }
  }

  private class ExportPlaintextTask extends AsyncTask<Void, Void, Integer> {
    private ProgressDialog dialog;

    @Override
    protected void onPreExecute() {
      dialog = ProgressDialog.show(getActivity(),
                                   getActivity().getString(R.string.ExportFragment_exporting),
                                   getActivity().getString(R.string.ExportFragment_exporting_plaintext_to_storage),
                                   true, false);
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        PlaintextBackupExporter.exportPlaintextToSd(getActivity(), masterSecret);
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ExportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ExportFragment", e);
        return ERROR_IO;
      }
    }

    @Override
    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (dialog != null)
        dialog.dismiss();

      if (context == null)
        return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_error_unable_to_write_to_storage),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_error_while_writing_to_storage),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_export_successful),
                         Toast.LENGTH_LONG).show();
          break;
      }
    }
  }


}
