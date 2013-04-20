package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;

import java.util.SortedSet;
import java.util.TreeSet;

public class DatabaseUpgradeActivity extends Activity {

  public static final int NO_MORE_KEY_EXCHANGE_PREFIX_VERSION = 46;

  private static final String LAST_VERSION_CODE = "last_version_code";

  private static final SortedSet<Integer> UPGRADE_VERSIONS = new TreeSet<Integer>() {{
    add(NO_MORE_KEY_EXCHANGE_PREFIX_VERSION);
  }};

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = (MasterSecret)getIntent().getParcelableExtra("master_secret");

    if (needsDatabaseUpgrade()) {
      Log.w("DatabaseUpgradeActivity", "Upgrading...");
      setContentView(R.layout.database_upgrade_activity);

      ProgressBar indeterminateProgress = (ProgressBar)findViewById(R.id.indeterminate_progress);
      ProgressBar determinateProgress   = (ProgressBar)findViewById(R.id.determinate_progress);

      new DatabaseUpgradeTask(indeterminateProgress, determinateProgress).execute(getLastSeenVersion());
    } else {
      updateLastSeenVersion();
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }
  }

  private boolean needsDatabaseUpgrade() {
    try {
      int currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
      int lastSeenVersion    = getLastSeenVersion();

      Log.w("DatabaseUpgradeActivity", "LastSeenVersion: " + lastSeenVersion);

      if (lastSeenVersion >= currentVersionCode)
        return false;

      for (int version : UPGRADE_VERSIONS) {
        Log.w("DatabaseUpgradeActivity", "Comparing: " + version);
        if (lastSeenVersion < version)
          return true;
      }

      return false;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  private int getLastSeenVersion() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    return preferences.getInt(LAST_VERSION_CODE, 0);
  }

  private void updateLastSeenVersion() {
    try {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
      int currentVersionCode        = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
      preferences.edit().putInt(LAST_VERSION_CODE, currentVersionCode).commit();
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  public static boolean isUpdate(Context context) {
    try {
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      int currentVersionCode        = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
      int previousVersionCode       = preferences.getInt(LAST_VERSION_CODE, 0);

      return previousVersionCode < currentVersionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  public interface DatabaseUpgradeListener {
    public void setProgress(int progress, int total);
  }

  private class DatabaseUpgradeTask extends AsyncTask<Integer, Double, Void>
      implements DatabaseUpgradeListener
  {

    private final ProgressBar indeterminateProgress;
    private final ProgressBar determinateProgress;

    public DatabaseUpgradeTask(ProgressBar indeterminateProgress, ProgressBar determinateProgress) {
      this.indeterminateProgress = indeterminateProgress;
      this.determinateProgress   = determinateProgress;
    }

    @Override
    protected Void doInBackground(Integer... params) {
      Log.w("DatabaseUpgradeActivity", "Running background upgrade..");
      DatabaseFactory.getInstance(DatabaseUpgradeActivity.this)
                     .onApplicationLevelUpgrade(masterSecret, params[0], this);
      return null;
    }

    @Override
    protected void onProgressUpdate(Double... update) {
      indeterminateProgress.setVisibility(View.GONE);
      determinateProgress.setVisibility(View.VISIBLE);

      double scaler = update[0];
      determinateProgress.setProgress((int)Math.floor(determinateProgress.getMax() * scaler));
    }

    @Override
    protected void onPostExecute(Void result) {
      updateLastSeenVersion();
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }

    @Override
    public void setProgress(int progress, int total) {
      publishProgress(((double)progress / (double)total));
    }
  }

}
