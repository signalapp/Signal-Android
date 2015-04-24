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

  public ExternalMediaWarningDialog(Context context, WarningListener listener) {
    super(new MaterialDialog.Builder(context).title(R.string.ExternalMediaWarningDialog_expose_secure_media)
                                             .iconAttr(R.attr.dialog_alert_icon)
                                             .customView(R.layout.external_media_warning_view, false)
                                             .cancelable(true)
                                             .positiveText(R.string.ExternalMediaWarningDialog_continue)
                                             .negativeText(R.string.ExternalMediaWarningDialog_cancel)
                                             .callback(new DialogListener(listener)));
  }

  public static void showIfNecessary(Context context, WarningListener listener) {
    if (!TextSecurePreferences.isWarnOnExposeSecureMediaEnabled(context)) {
      listener.onWarningAccepted();
    } else {
      new ExternalMediaWarningDialog(context, listener).show();
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

  public static class DialogListener extends ButtonCallback {
    private final WarningListener listener;

    public DialogListener(WarningListener listener) {
      this.listener = listener;
    }

    public void onPositive(MaterialDialog dialog) {
      listener.onWarningAccepted();
    }
  }

  public interface WarningListener {
    public void onWarningAccepted();
  }

}
