package org.thoughtcrime.securesms.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;

public class Trimmer {

  public static void trimAllThreads(Context context, int threadLengthLimit) {
    new TrimmingProgressTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadLengthLimit);
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
      progressDialog.setTitle(R.string.trimmer__deleting);
      progressDialog.setMessage(context.getString(R.string.trimmer__deleting_old_messages));
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
                     R.string.trimmer__old_messages_successfully_deleted,
                     Toast.LENGTH_LONG).show();
    }

    @Override
    public void onProgress(int complete, int total) {
      this.publishProgress(complete, total);
    }
  }
}
