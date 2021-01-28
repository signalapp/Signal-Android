package org.thoughtcrime.securesms;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.SmsMigrator.ProgressDescription;
import org.thoughtcrime.securesms.service.ApplicationMigrationService;
import org.thoughtcrime.securesms.service.ApplicationMigrationService.ImportState;

public class DatabaseMigrationActivity extends PassphraseRequiredActivity {

  private final ImportServiceConnection serviceConnection  = new ImportServiceConnection();
  private final ImportStateHandler      importStateHandler = new ImportStateHandler();
  private final BroadcastReceiver       completedReceiver  = new NullReceiver();

  private LinearLayout promptLayout;
  private LinearLayout progressLayout;
  private Button       skipButton;
  private Button       importButton;
  private ProgressBar  progress;
  private TextView     progressLabel;

  private ApplicationMigrationService importService;
  private boolean isVisible = false;

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.database_migration_activity);

    initializeResources();
    initializeServiceBinding();
  }

  @Override
  public void onResume() {
    super.onResume();
    isVisible = true;
    registerForCompletedNotification();
  }

  @Override
  public void onPause() {
    super.onPause();
    isVisible = false;
    unregisterForCompletedNotification();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownServiceBinding();
  }

  @Override
  public void onBackPressed() {

  }

  private void initializeServiceBinding() {
    Intent intent = new Intent(this, ApplicationMigrationService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    this.promptLayout   = (LinearLayout)findViewById(R.id.prompt_layout);
    this.progressLayout = (LinearLayout)findViewById(R.id.progress_layout);
    this.skipButton     = (Button)      findViewById(R.id.skip_button);
    this.importButton   = (Button)      findViewById(R.id.import_button);
    this.progress       = (ProgressBar) findViewById(R.id.import_progress);
    this.progressLabel  = (TextView)    findViewById(R.id.import_status);

    this.progressLayout.setVisibility(View.GONE);
    this.promptLayout.setVisibility(View.GONE);

    this.importButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(DatabaseMigrationActivity.this, ApplicationMigrationService.class);
        intent.setAction(ApplicationMigrationService.MIGRATE_DATABASE);
        intent.putExtra("master_secret", (Parcelable)getIntent().getParcelableExtra("master_secret"));
        startService(intent);

        promptLayout.setVisibility(View.GONE);
        progressLayout.setVisibility(View.VISIBLE);
      }
    });

    this.skipButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ApplicationMigrationService.setDatabaseImported(DatabaseMigrationActivity.this);
        handleImportComplete();
      }
    });
  }

  private void registerForCompletedNotification() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(ApplicationMigrationService.COMPLETED_ACTION);
    filter.setPriority(1000);

    registerReceiver(completedReceiver, filter);
  }

  private void unregisterForCompletedNotification() {
    unregisterReceiver(completedReceiver);
  }

  private void shutdownServiceBinding() {
    unbindService(serviceConnection);
  }

  private void handleStateIdle() {
    this.promptLayout.setVisibility(View.VISIBLE);
    this.progressLayout.setVisibility(View.GONE);
  }

  private void handleStateProgress(ProgressDescription update) {
    this.promptLayout.setVisibility(View.GONE);
    this.progressLayout.setVisibility(View.VISIBLE);
    this.progressLabel.setText(update.primaryComplete + "/" + update.primaryTotal);

    double max               = this.progress.getMax();
    double primaryTotal      = update.primaryTotal;
    double primaryComplete   = update.primaryComplete;
    double secondaryTotal    = update.secondaryTotal;
    double secondaryComplete = update.secondaryComplete;

    this.progress.setProgress((int)Math.round((primaryComplete / primaryTotal) * max));
    this.progress.setSecondaryProgress((int)Math.round((secondaryComplete / secondaryTotal) * max));
  }

  private void handleImportComplete() {
    if (isVisible) {
      if (getIntent().hasExtra("next_intent")) {
        startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      } else {
        // TODO [greyson] Navigation
        startActivity(MainActivity.clearTop(this));
      }
    }

    finish();
  }

  private class ImportStateHandler extends Handler {

    public ImportStateHandler() {
      super(Looper.getMainLooper());
    }

    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
      case ImportState.STATE_IDLE:                   handleStateIdle();                                     break;
      case ImportState.STATE_MIGRATING_IN_PROGRESS:  handleStateProgress((ProgressDescription)message.obj); break;
      case ImportState.STATE_MIGRATING_COMPLETE:     handleImportComplete();                                break;
      }
    }
  }

  private class ImportServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      importService  = ((ApplicationMigrationService.ApplicationMigrationBinder)service).getService();
      importService.setImportStateHandler(importStateHandler);

      ImportState state = importService.getState();
      importStateHandler.obtainMessage(state.state, state.progress).sendToTarget();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      importService.setImportStateHandler(null);
    }
  }

  private static class NullReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      abortBroadcast();
    }
  }


}
