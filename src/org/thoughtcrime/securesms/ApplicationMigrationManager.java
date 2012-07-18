package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.SmsMigrator;

public class ApplicationMigrationManager extends Handler implements Runnable {
  private ProgressDialog progressDialog;

  private final Context context;
  private final MasterSecret masterSecret;

  private ApplicationMigrationListener listener;

  public ApplicationMigrationManager(Context context,
                                     MasterSecret masterSecret)
  {
    this.masterSecret = masterSecret;
    this.context      = context;
  }

  public void setMigrationListener(ApplicationMigrationListener listener) {
    this.listener = listener;
  }

  public void run() {
    SmsMigrator.migrateDatabase(context, masterSecret, this);
  }

  public void migrate() {
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
    alertBuilder.setTitle("Copy System Text Message Database?");
    alertBuilder.setMessage("Current versions of TextSecure use an encrypted database that is " +
                            "separate from the default system database.  Would you like to " +
                            "copy your existing text messages into TextSecure's encrypted " +
                            "database?  Your default system database will be unaffected.");
    alertBuilder.setCancelable(false);
    alertBuilder.setPositiveButton("Copy", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Migrating Database");
        progressDialog.setMessage("Migrating your SMS database...");
        progressDialog.setMax(10000);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.show();

        new Thread(ApplicationMigrationManager.this).start();
      }
    });

    alertBuilder.setNegativeButton("Don't copy", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE)
          .edit()
          .putBoolean("migrated", true).commit();
        listener.applicationMigrationComplete();
      }
    });

    alertBuilder.create().show();
  }

  @Override
  public void handleMessage(Message message) {
    switch (message.what) {
    case SmsMigrator.PROGRESS_UPDATE:
      progressDialog.incrementProgressBy(message.arg1);
      progressDialog.setSecondaryProgress(0);
      break;
    case SmsMigrator.SECONDARY_PROGRESS_UPDATE:
      progressDialog.incrementSecondaryProgressBy(message.arg1);
      break;
    case SmsMigrator.COMPLETE:
      progressDialog.dismiss();
      listener.applicationMigrationComplete();
      break;
    }
  }

  public static interface ApplicationMigrationListener {
    public void applicationMigrationComplete();
  }
}
