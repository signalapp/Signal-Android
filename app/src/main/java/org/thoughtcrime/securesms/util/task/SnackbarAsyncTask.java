package org.thoughtcrime.securesms.util.task;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import android.view.View;

public abstract class SnackbarAsyncTask<Params>
    extends AsyncTask<Params, Void, Void>
    implements View.OnClickListener
{

  private final View    view;
  private final String  snackbarText;
  private final String  snackbarActionText;
  private final int     snackbarActionColor;
  private final int     snackbarDuration;
  private final boolean showProgress;

  private @Nullable Params         reversibleParameter;
  private @Nullable ProgressDialog progressDialog;

  public SnackbarAsyncTask(View view,
                           String snackbarText,
                           String snackbarActionText,
                           int snackbarActionColor,
                           int snackbarDuration,
                           boolean showProgress)
  {
    this.view                = view;
    this.snackbarText        = snackbarText;
    this.snackbarActionText  = snackbarActionText;
    this.snackbarActionColor = snackbarActionColor;
    this.snackbarDuration    = snackbarDuration;
    this.showProgress        = showProgress;
  }

  @Override
  protected void onPreExecute() {
    if (this.showProgress) this.progressDialog = ProgressDialog.show(view.getContext(), "", "", true);
    else                   this.progressDialog = null;
  }

  @SafeVarargs
  @Override
  protected final Void doInBackground(Params... params) {
    this.reversibleParameter = params != null && params.length > 0 ?params[0] : null;
    executeAction(reversibleParameter);
    return null;
  }

  @Override
  protected void onPostExecute(Void result) {
    if (this.showProgress && this.progressDialog != null) {
      this.progressDialog.dismiss();
      this.progressDialog = null;
    }

    Snackbar.make(view, snackbarText, snackbarDuration)
            .setAction(snackbarActionText, this)
            .setActionTextColor(snackbarActionColor)
            .show();
  }

  @Override
  public void onClick(View v) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected void onPreExecute() {
        if (showProgress) progressDialog = ProgressDialog.show(view.getContext(), "", "", true);
        else              progressDialog = null;
      }

      @Override
      protected Void doInBackground(Void... params) {
        reverseAction(reversibleParameter);
        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        if (showProgress && progressDialog != null) {
          progressDialog.dismiss();
          progressDialog = null;
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  protected abstract void executeAction(@Nullable Params parameter);
  protected abstract void reverseAction(@Nullable Params parameter);

}
