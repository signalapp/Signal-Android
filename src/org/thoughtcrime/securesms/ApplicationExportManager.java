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
    alertBuilder.setTitle("Import Database and Settings?");
    alertBuilder.setMessage("Import TextSecure database, keys, and settings from the SD Card?" +
                            "\n\nWARNING: This will clobber any existing messages, keys, and " +
                            "settings!");
    alertBuilder.setCancelable(false);
    alertBuilder.setPositiveButton("Import", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        task           = TASK_IMPORT;
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Importing Database and Keys");
        progressDialog.setMessage("Importnig your SMS database, keys, and settings...");
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();

        if (listener != null)
          listener.onPrepareForImport();

        new Thread(ApplicationExportManager.this).start();
      }
    });

    alertBuilder.setNegativeButton("Cancel", null);
    alertBuilder.create().show();
  }


  public void exportDatabase() {
    Log.w("ApplicationExportManager", "Context: " + context);
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
    alertBuilder.setTitle("Export Database?");
    alertBuilder.setMessage("Export TextSecure database, keys, and settings to the SD Card?");
    alertBuilder.setCancelable(false);

    alertBuilder.setPositiveButton("Export", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        task           = TASK_EXPORT;
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Exporting Database and Keys");
        progressDialog.setMessage("Exporting your SMS database, keys, and settings...");
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();

        new Thread(ApplicationExportManager.this).start();
      }
    });

    alertBuilder.setNegativeButton("Cancel", null);
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
      Toast.makeText(context, "No SD card found!", Toast.LENGTH_LONG).show();
      break;
    case ERROR_IO:
      Toast.makeText(context, "Error exporting to SD!", Toast.LENGTH_LONG).show();
      break;
    case COMPLETE:
      switch (task) {
      case TASK_IMPORT:
        Toast.makeText(context, "Import Successful!", Toast.LENGTH_LONG).show();
        break;
      case TASK_EXPORT:
        Toast.makeText(context, "Export Successful!", Toast.LENGTH_LONG).show();
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
