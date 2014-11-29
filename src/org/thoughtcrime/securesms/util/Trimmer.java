package org.thoughtcrime.securesms.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.R;

public class Trimmer {

  public static void trimAllThreads(Context context, int threadLengthLimit) {
    new TrimmingProgressTask(context).execute(threadLengthLimit);
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
      progressDialog.setTitle(context.getString(R.string.Trimmer__deleting_old_messages_title));
      progressDialog.setMessage(context.getString(R.string.Trimmer__deleting_old_messages_message));
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
                     context.getString(R.string.Trimmer__old_messages_successfully_deleted_toast),
                     Toast.LENGTH_LONG).show();
    }

    @Override
    public void onProgress(int complete, int total) {
      this.publishProgress(complete, total);
    }
  }
}
