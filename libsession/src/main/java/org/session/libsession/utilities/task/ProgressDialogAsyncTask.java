package org.session.libsession.utilities.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

public abstract class ProgressDialogAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

  private final WeakReference<Context> contextReference;
  private       ProgressDialog         progress;
  private final String                 title;
  private final String                 message;

  public ProgressDialogAsyncTask(@NonNull Context context, @NonNull String title, @NonNull String message) {
    super();
    this.contextReference = new WeakReference<>(context);
    this.title            = title;
    this.message          = message;
  }

  public ProgressDialogAsyncTask(@NonNull Context context, int title, int message) {
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

  protected @NonNull Context getContext() {
    return contextReference.get();
  }
}

