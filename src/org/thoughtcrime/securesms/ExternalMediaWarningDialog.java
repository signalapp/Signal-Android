package org.thoughtcrime.securesms;

import android.content.Context;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.afollestad.materialdialogs.MaterialDialog;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * rhodey
 */
public class ExternalMediaWarningDialog extends MaterialDialog implements OnCheckedChangeListener {

  public ExternalMediaWarningDialog(Context context, ButtonCallback callback) {
    super(new MaterialDialog.Builder(context).title(R.string.ExternalMediaWarningDialog_reveal_secure_media)
                                             .iconAttr(R.attr.dialog_alert_icon)
                                             .customView(R.layout.external_media_warning_view, false)
                                             .cancelable(true)
                                             .positiveText(R.string.ExternalMediaWarningDialog_reveal)
                                             .negativeText(R.string.ExternalMediaWarningDialog_cancel)
                                             .callback(callback));
  }

  public static void showIfNecessary(Context context, ButtonCallback callback) {
    if (!TextSecurePreferences.isWarnOnExposeSecureMediaEnabled(context)) {
      callback.onPositive(null);
    } else {
      new ExternalMediaWarningDialog(context, callback).show();
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((CheckBox)findViewById(R.id.checkbox_dont_ask_again)).setOnCheckedChangeListener(this);
  }

  @Override
  public void onCheckedChanged(CompoundButton checkBox, boolean isChecked) {
    TextSecurePreferences.setWarnOnExposeSecureMediaEnabled(getContext(), !isChecked);
  }

}
