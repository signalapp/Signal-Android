/**
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;

public class Trimmer {

  public static void trimAllThreads(Context context, int threadLengthLimit) {
    new TrimmingProgressTask(context).execute(threadLengthLimit);
  }

  public static void trimThread(final Context context, final long threadId) {
          boolean trimmingEnabled   = TextSecurePreferences.isThreadLengthTrimmingEnabled(context);
    final int     threadLengthLimit = TextSecurePreferences.getThreadTrimLength(context);

    if (!trimmingEnabled)
      return;

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getThreadDatabase(context).trimThread(threadId, threadLengthLimit);
        return null;
      }
    }.execute();
  }

  private static class TrimmingProgressTask extends AsyncTask<Integer, Integer, Void> implements ThreadDatabase.ProgressListener {
    private ProgressDialog progressDialog;
    private Context context;

    public TrimmingProgressTask(Context context) {
      this.context = context;
    }

    @Override
    protected void onPreExecute() {
      progressDialog = new ProgressDialog(context);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(false);
      progressDialog.setTitle("Deleting...");
      progressDialog.setMessage("Deleting old messages...");
      progressDialog.setMax(100);
      progressDialog.show();
    }

    @Override
    protected Void doInBackground(Integer... params) {
      DatabaseFactory.getThreadDatabase(context).trimAllThreads(params[0], this);
      return null;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
      double count = progress[1];
      double index = progress[0];

      progressDialog.setProgress((int)Math.round((index / count) * 100.0));
    }

    @Override
    protected void onPostExecute(Void result) {
      progressDialog.dismiss();
      Toast.makeText(context,
                     "Old messages successfully deleted!",
                     Toast.LENGTH_LONG).show();
    }

    @Override
    public void onProgress(int complete, int total) {
      this.publishProgress(complete, total);
    }
  }
}
