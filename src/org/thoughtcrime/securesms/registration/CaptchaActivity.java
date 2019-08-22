package org.thoughtcrime.securesms.registration;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;

public class CaptchaActivity extends BaseActionBarActivity {

  public static final String KEY_TOKEN  = "token";
  public static final String KEY_IS_SMS = "is_sms";

  private static final String SIGNAL_SCHEME = "signalcaptcha://";

  public static Intent getIntent(@NonNull Context context, boolean isSms) {
    Intent intent = new Intent(context, CaptchaActivity.class);
    intent.putExtra(KEY_IS_SMS, isSms);
    return intent;
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.captcha_activity);

    WebView webView = findViewById(R.id.registration_captcha_web_view);

    webView.getSettings().setJavaScriptEnabled(true);
    webView.clearCache(true);

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url != null && url.startsWith(SIGNAL_SCHEME)) {
          handleToken(url.substring(SIGNAL_SCHEME.length()));
          return true;
        }
        return false;
      }
    });

    webView.loadUrl("https://signalcaptchas.org/registration/generate.html");
  }

  public void handleToken(String token) {
    if (!TextUtils.isEmpty(token)) {
      Intent result = new Intent();
      result.putExtra(KEY_TOKEN, token);
      result.putExtra(KEY_IS_SMS, getIntent().getBooleanExtra(KEY_IS_SMS, true));
      setResult(RESULT_OK, result);
    } else {
      setResult(RESULT_CANCELED);
    }

    finish();
  }
}
