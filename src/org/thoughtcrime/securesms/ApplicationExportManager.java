package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.ApplicationExporter;
import org.thoughtcrime.securesms.database.NoExternalStorageException;

import java.io.IOException;

public class ApplicationExportManager extends Handler implements Runnable {

  private static final int ERROR_NO_SD = 0;
  private static final int ERROR_IO    = 1;
  private static final int COMPLETE    = 2;

  private static final int TASK_EXPORT = 0;
  private static final int TASK_IMPORT = 1;

  private int task;
  private ProgressDialog progressDialog;
  private ApplicationExportListener listener;

  private final Context context;

  public ApplicationExportManager(Context context) {
    this.context = context;
  }

  public void setListener(ApplicationExportListener listener) {
    this.listener = listener;
  }

  public void importDatabase() {
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
    alertBuilder.setTitle(R.string.import_database_and_settings_title);
    alertBuilder.setMessage(R.string.import_database_and_settings_message);
    alertBuilder.setCancelable(false);
    alertBuilder.setPositiveButton(R.string.menu_import, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        task           = TASK_IMPORT;
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(context.getString(R.string.importing_database_and_keys));
        progressDialog.setMessage(context
            .getString(R.string.importing_your_sms_database_keys_and_settings));
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();

        if (listener != null)
          listener.onPrepareForImport();

        new Thread(ApplicationExportManager.this).start();
      }
    });

    alertBuilder.setNegativeButton(android.R.string.cancel, null);
    alertBuilder.create().show();
  }


  public void exportDatabase() {
    Log.w("ApplicationExportManager", "Context: " + context);
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
    alertBuilder.setTitle(R.string.export_database_question);
    alertBuilder.setMessage(R.string.export_textsecure_database_keys_and_settings_prompt);
    alertBuilder.setCancelable(false);
    
    alertBuilder.setPositiveButton(R.string.menu_export, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        task           = TASK_EXPORT;
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(context.getString(R.string.exporting_database_and_keys));
        progressDialog.setMessage(context
            .getString(R.string.exporting_your_sms_database_keys_and_settings));
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();

        new Thread(ApplicationExportManager.this).start();
      }
    });

    alertBuilder.setNegativeButton(android.R.string.cancel, null);
    alertBuilder.create().show();
  }


  public void run() {
    try {
      switch (task) {
      case TASK_EXPORT: ApplicationExporter.exportToSd(context);   break;
      case TASK_IMPORT: ApplicationExporter.importFromSd(context); break;
      }
    } catch (NoExternalStorageException e) {
      Log.w("SecureSMS", e);
      this.obtainMessage(ERROR_NO_SD).sendToTarget();
      return;
    } catch (IOException e) {
      Log.w("SecureSMS", e);
      this.obtainMessage(ERROR_IO).sendToTarget();
      return;
    }

    this.obtainMessage(COMPLETE).sendToTarget();
  }

  @Override
  public void handleMessage(Message message) {
    switch (message.what) {
    case ERROR_NO_SD:
      Toast.makeText(context, R.string.no_sd_card_found_exclamation, Toast.LENGTH_LONG).show();
      break;
    case ERROR_IO:
      Toast.makeText(context, R.string.error_exporting_to_sd_exclamation, Toast.LENGTH_LONG).show();
      break;
    case COMPLETE:
      switch (task) {
      case TASK_IMPORT:
        Toast.makeText(context, R.string.import_successful_exclamation, Toast.LENGTH_LONG).show();
        break;
      case TASK_EXPORT:
        Toast.makeText(context, R.string.export_successful_exclamation, Toast.LENGTH_LONG).show();
        break;
      }
      break;
    }

    progressDialog.dismiss();
  }

  public interface ApplicationExportListener {
    public void onPrepareForImport();
  }
}
