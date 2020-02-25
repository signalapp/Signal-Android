package org.thoughtcrime.securesms.util.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

public abstract class ProgressDialogAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

  private final WeakReference<Context> contextReference;
  private       ProgressDialog         progress;
  private final String                 title;
  private final String                 message;

  public ProgressDialogAsyncTask(Context context, String title, String message) {
    super();
    this.contextReference = new WeakReference<>(context);
    this.title            = title;
    this.message          = message;
  }

  public ProgressDialogAsyncTask(Context context, int title, int message) {
    this(context, context.getString(title), context.getString(message));
  }

  @Override
  protected void onPreExecute() {
    final Context context = contextReference.get();
    if (context != null) progress = ProgressDialog.show(context, title, message, true);
  }

  @Override
  protected void onPostExecute(Result result) {
    if (progress != null) progress.dismiss();
  }

  protected Context getContext() {
    return contextReference.get();
  }
}

