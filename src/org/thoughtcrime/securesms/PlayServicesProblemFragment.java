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

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.MaterialDialog.ButtonCallback;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class PlayServicesProblemFragment extends DialogFragment {

  private Dialog getPlayServicesInvalidDialog() {
    return new MaterialDialog.Builder(getActivity())
               .content(R.string.PlayServicesProblemFragment__please_install_an_authentic_version_of_google_play)
               .cancelable(false)
               .positiveText(android.R.string.ok)
               .callback(new ButtonCallback() {
                 @Override
                 public void onPositive(MaterialDialog dialog) {
                   super.onPositive(dialog);
                   getActivity().finish();
                 }
               })
               .build();
  }

  @Override
  @NonNull
  public Dialog onCreateDialog(Bundle bundle) {
    int code = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());

    switch (code) {
      case ConnectionResult.SERVICE_MISSING:
      case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
      case ConnectionResult.SERVICE_DISABLED:
        return GooglePlayServicesUtil.getErrorDialog(code, getActivity(), 9111);

      default:
        Log.w(getClass().getName(), "received error code " + code);
        return getPlayServicesInvalidDialog();
    }
  }

}
