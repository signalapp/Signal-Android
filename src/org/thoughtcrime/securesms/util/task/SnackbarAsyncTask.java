package org.thoughtcrime.securesms.util.task;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;

public abstract class SnackbarAsyncTask<Params>
    extends AsyncTask<Params, Void, Void>
    implements View.OnClickListener
{

  private final View   view;
  private final String snackbarText;
  private final String snackbarActionText;
  private final int    snackbarActionColor;
  private final int    snackbarDuration;

  private @Nullable Params reversibleParameter;

  public SnackbarAsyncTask(View view,
                           String snackbarText,
                           String snackbarActionText,
                           int snackbarActionColor,
                           int snackbarDuration)
  {
    this.view                = view;
    this.snackbarText        = snackbarText;
    this.snackbarActionText  = snackbarActionText;
    this.snackbarActionColor = snackbarActionColor;
    this.snackbarDuration    = snackbarDuration;
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
    Snackbar.make(view, snackbarText, snackbarDuration)
            .setAction(snackbarActionText, this)
            .setActionTextColor(snackbarActionColor)
            .show();
  }

  @Override
  public void onClick(View v) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        reverseAction(reversibleParameter);
        return null;
      }
    }.execute();
  }

  protected abstract void executeAction(@Nullable Params parameter);
  protected abstract void reverseAction(@Nullable Params parameter);

}
