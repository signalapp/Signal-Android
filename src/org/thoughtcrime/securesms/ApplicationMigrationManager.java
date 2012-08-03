package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.service.ApplicationMigrationService;

public class ApplicationMigrationManager extends Handler {

  private ProgressDialog progressDialog;
  private ApplicationMigrationListener listener;

  private final Context context;
  private final MasterSecret masterSecret;

  public ApplicationMigrationManager(Context context,
                                     MasterSecret masterSecret)
  {
    this.masterSecret = masterSecret;
    this.context      = context;
  }

  public void setMigrationListener(ApplicationMigrationListener listener) {
    this.listener = listener;
  }

  private void displayMigrationProgress() {
    progressDialog = new ProgressDialog(context);
    progressDialog.setTitle("Migrating Database");
    progressDialog.setMessage("Migrating text message database...");
    progressDialog.setMax(10000);
    progressDialog.setCancelable(false);
    progressDialog.setIndeterminate(false);
    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    progressDialog.show();
  }

  public void migrate() {
    context.bindService(new Intent(context, ApplicationMigrationService.class),
                        serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void displayMigrationPrompt() {
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
    alertBuilder.setTitle("Copy System Text Message Database?");
    alertBuilder.setMessage("TextSecure uses an encrypted database that is " +
                            "separate from the default system database.  Would you like to " +
                            "copy your existing text messages into TextSecure's encrypted " +
                            "database?  Your default system database will be unaffected.");
    alertBuilder.setCancelable(false);

    alertBuilder.setPositiveButton("Copy", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        displayMigrationProgress();
        Intent intent = new Intent(context, ApplicationMigrationService.class);
        intent.setAction(ApplicationMigrationService.MIGRATE_DATABASE);
        intent.putExtra("master_secret", masterSecret);
        context.startService(intent);
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
    case ApplicationMigrationService.PROGRESS_UPDATE:
      if (progressDialog != null) {
        progressDialog.setProgress(message.arg1);
        progressDialog.setSecondaryProgress(message.arg2);
      }
      break;
    case ApplicationMigrationService.PROGRESS_COMPLETE:
      if (progressDialog != null) {
        progressDialog.dismiss();
      }

      if (listener != null) {
        listener.applicationMigrationComplete();
      }
      break;
    }
  }

  public static interface ApplicationMigrationListener {
    public void applicationMigrationComplete();
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      ApplicationMigrationService applicationMigrationService
        = ((ApplicationMigrationService.ApplicationMigrationBinder)service).getService();

      if (applicationMigrationService.isMigrating()) displayMigrationProgress();
      else                                           displayMigrationPrompt();

      applicationMigrationService.setHandler(ApplicationMigrationManager.this);
    }

    public void onServiceDisconnected(ComponentName name) {}
  };

}
