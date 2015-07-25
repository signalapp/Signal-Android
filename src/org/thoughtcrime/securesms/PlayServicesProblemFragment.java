/**
 * Copyright (C) 2014 Open Whisper Systems
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

package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class PlayServicesProblemFragment extends DialogFragment {

  @Override
  public @NonNull Dialog onCreateDialog(@NonNull Bundle bundle) {
    int    code   = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());
    Dialog dialog = GooglePlayServicesUtil.getErrorDialog(code, getActivity(), 9111);

    if (dialog == null) {
      return new MaterialDialog.Builder(getActivity()).negativeText(android.R.string.ok)
                                                      .content(getActivity().getString(R.string.PlayServicesProblemFragment_the_version_of_google_play_services_you_have_installed_is_not_functioning))
                                                      .build();
    } else {
      return dialog;
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    super.onCancel(dialog);
    finish();
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    super.onDismiss(dialog);
    finish();
  }

  private void finish() {
    Activity activity = getActivity();
    if (activity != null) activity.finish();
  }
  
}
