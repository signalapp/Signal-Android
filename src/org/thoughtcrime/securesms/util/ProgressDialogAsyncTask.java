/**
 * Copyright (C) 2014 Open WhisperSystems
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

public abstract class ProgressDialogAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
  private final Context        context;
  private       ProgressDialog progress;
  private final String         title;
  private final String         message;

  public ProgressDialogAsyncTask(Context context, String title, String message) {
    super();
    this.context = context;
    this.title   = title;
    this.message = message;
  }

  public ProgressDialogAsyncTask(Context context, int title, int message) {
    this(context, context.getString(title), context.getString(message));
  }

  @Override
  protected void onPreExecute() {
    progress = ProgressDialog.show(context, title, message, true);
  }

  @Override
  protected void onPostExecute(Result result) {
    if (progress != null) progress.dismiss();
  }
}

