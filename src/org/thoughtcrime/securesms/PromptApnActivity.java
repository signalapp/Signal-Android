package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

public class PromptApnActivity extends PassphraseRequiredSherlockActivity {

  private EditText mmsc;
  private EditText proxyHost;
  private EditText proxyPort;

  private Button okButton;
  private Button cancelButton;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.prompt_apn_activity);

    initializeResources();
    initializeValues();
  }

  private void initializeValues() {
    mmsc.setText(TextSecurePreferences.getMmscUrl(this));
    proxyHost.setText(TextSecurePreferences.getMmscProxy(this));
    proxyPort.setText(TextSecurePreferences.getMmscProxyPort(this));
  }

  private void initializeResources() {
    this.mmsc         = (EditText)findViewById(R.id.mmsc_url);
    this.proxyHost    = (EditText)findViewById(R.id.mms_proxy_host);
    this.proxyPort    = (EditText)findViewById(R.id.mms_proxy_port);
    this.okButton     = (Button)findViewById(R.id.ok_button);
    this.cancelButton = (Button)findViewById(R.id.cancel_button);

    this.okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleSettings();
      }
    });

    this.cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }

  private void handleSettings() {
    if (Util.isEmpty(mmsc)) {
      Toast.makeText(this, R.string.PromptApnActivity_you_must_specify_an_mmsc_url_for_your_carrier, Toast.LENGTH_LONG).show();
      return;
    }

    PreferenceManager.getDefaultSharedPreferences(this).edit()
      .putBoolean(ApplicationPreferencesActivity.USE_LOCAL_MMS_APNS_PREF, true)
      .putString(ApplicationPreferencesActivity.MMSC_HOST_PREF, mmsc.getText().toString().trim())
      .putString(ApplicationPreferencesActivity.MMSC_PROXY_HOST_PREF, proxyHost.getText().toString().trim())
      .putString(ApplicationPreferencesActivity.MMSC_PROXY_PORT_PREF, proxyPort.getText().toString().trim())
      .commit();

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.PromptApnActivity_mms_settings_updated);
    builder.setMessage(R.string.PromptApnActivity_you_can_modify_these_values_from_the_textsecure_settings_menu_at_any_time_);
    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        finish();
      }
    });
    builder.show();
  }
}
