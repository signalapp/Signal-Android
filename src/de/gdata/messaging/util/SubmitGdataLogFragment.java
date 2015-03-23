package de.gdata.messaging.util;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.thoughtcrime.securesms.R;
import org.whispersystems.libpastelog.SubmitLogFragment;
import org.whispersystems.libpastelog.util.Scrubber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
public class SubmitGdataLogFragment extends SubmitLogFragment {

  private EditText logPreview;
  private Button okButton;
  private Button cancelButton;
  private OnLogSubmittedListener mListener;

  private class PopulateLogcatAsyncTask extends AsyncTask<Void, Void, String> {

    @Override
    protected String doInBackground(Void... voids) {
      final StringBuilder builder = new StringBuilder(buildDescription(getActivity()));
      builder.append("\n");
      builder.append(new Scrubber().scrub(grabLogcat()));
      return builder.toString();
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      logPreview.setText(R.string.log_submit_activity__loading_logs);
      okButton.setEnabled(false);
    }

    @Override
    protected void onPostExecute(String logcat) {
      super.onPostExecute(logcat);
      if (TextUtils.isEmpty(logcat)) {
        if (mListener != null) mListener.onFailure();
        return;
      }
      logPreview.setText(logcat);
      okButton.setEnabled(true);
    }
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeResources();
  }

  private void initializeResources() {
    logPreview = (EditText) getView().findViewById(R.id.log_preview);
    okButton = (Button) getView().findViewById(R.id.ok);
    cancelButton = (Button) getView().findViewById(R.id.cancel);

    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
            Intent email = new Intent(Intent.ACTION_SEND);
            email.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.tk_mail)});
            email.putExtra(Intent.EXTRA_SUBJECT, "DebugLogs");
            email.putExtra(Intent.EXTRA_TEXT, logPreview.getText().toString());
            email.setType("message/rfc822");
            startActivity(Intent.createChooser(email, "Choose an Email client :"));
          }
    });

    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mListener != null) mListener.onCancel();
        getActivity().finish();
      }
    });
    new PopulateLogcatAsyncTask().execute();
  }

  private static String grabLogcat() {
    try {
      final Process process = Runtime.getRuntime().exec("logcat -d");
      final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      final StringBuilder log = new StringBuilder();
      final String separator = System.getProperty("line.separator");

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log.toString();
    } catch (IOException ioe) {
      Log.w("GDATA", "IOException when trying to read logcat.", ioe);
      return null;
    }
  }
  public static SubmitGdataLogFragment newInstance()
  {
    SubmitGdataLogFragment fragment = new SubmitGdataLogFragment();

    return fragment;
  }
  private static String buildDescription(Context context) {
    final PackageManager pm = context.getPackageManager();
    final StringBuilder builder = new StringBuilder();


    builder.append("Device  : ")
        .append(Build.MANUFACTURER).append(" ")
        .append(Build.MODEL).append(" (")
        .append(Build.PRODUCT).append(")\n");
    builder.append("Android : ").append(Build.VERSION.RELEASE).append(" (")
        .append(Build.VERSION.INCREMENTAL).append(", ")
        .append(Build.DISPLAY).append(")\n");
    builder.append("Memory  : ").append(getMemoryUsage(context)).append("\n");
    builder.append("Memclass: ").append(getMemoryClass(context)).append("\n");
    builder.append("OS Host : ").append(Build.HOST).append("\n");
    builder.append("App     : ");
    try {
      builder.append(pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)))
          .append(" ")
          .append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
          .append("\n");
    } catch (PackageManager.NameNotFoundException nnfe) {
      builder.append("Unknown\n");
    }

    return builder.toString();
  }

  public static String getMemoryUsage(Context context) {
    Runtime info = Runtime.getRuntime();
    info.totalMemory();
    return String.format(Locale.ENGLISH, "%dM (%.2f%% used, %dM max)",
        asMegs(info.totalMemory()),
        (float) info.freeMemory() / info.totalMemory(),
        asMegs(info.maxMemory()));
  }

  private static long asMegs(long bytes) {
    return bytes / 1048576L;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public static String getMemoryClass(Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    String lowMem = "";

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && activityManager.isLowRamDevice()) {
      lowMem = ", low-mem device";
    }
    return activityManager.getMemoryClass() + lowMem;
  }
}
