package org.thoughtcrime.securesms.ratelimit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

/**
 * Asks the user to solve a reCAPTCHA. If successful, triggers resends of all relevant message jobs.
 */
public class RecaptchaProofActivity extends PassphraseRequiredActivity {
  private static final String TAG = Log.tag(RecaptchaProofActivity.class);

  private static final String RECAPTCHA_SCHEME = "signalcaptcha://";

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  @SuppressLint("SetJavaScriptEnabled")
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    setContentView(R.layout.recaptcha_activity);

    requireSupportActionBar().setDisplayHomeAsUpEnabled(true);
    requireSupportActionBar().setTitle(R.string.RecaptchaProofActivity_complete_verification);

    WebView webView = findViewById(R.id.recaptcha_webview);
    webView.getSettings().setJavaScriptEnabled(true);
    webView.clearCache(true);
    webView.setBackgroundColor(Color.TRANSPARENT);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url != null && url.startsWith(RECAPTCHA_SCHEME)) {
          handleToken(url.substring(RECAPTCHA_SCHEME.length()));
          return true;
        }
        return false;
      }
    });

    webView.loadUrl(BuildConfig.RECAPTCHA_PROOF_URL);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void handleToken(@NonNull String token) {
    SimpleProgressDialog.DismissibleDialog dialog = SimpleProgressDialog.showDelayed(this, 1000, 500);
    SimpleTask.run(() -> {
      String challenge = SignalStore.rateLimit().getChallenge();
      if (Util.isEmpty(challenge)) {
        Log.w(TAG, "No challenge available?");
        return new TokenResult(true, false);
      }

      try {
        for (int i = 0; i < 3; i++) {
          try {
            AppDependencies.getSignalServiceAccountManager().submitRateLimitRecaptchaChallenge(challenge, token);
            RateLimitUtil.retryAllRateLimitedMessages(this);
            Log.i(TAG, "Successfully completed reCAPTCHA.");
            return new TokenResult(true, true);
          } catch (PushNetworkException e) {
            Log.w(TAG, "Network error during submission. Retrying.", e);
          }
        }
      } catch (IOException e) {
        Log.w(TAG, "Terminal failure during submission. Will clear state. May get a 428 later.", e);
        return new TokenResult(true, false);
      }

      return new TokenResult(false, false);
    }, result -> {
      dialog.dismiss();

      if (result.clearState) {
        Log.i(TAG, "Considering the response sufficient to clear the slate.");
        SignalStore.rateLimit().onProofAccepted();
        setResult(RESULT_OK);
      }

      if (!result.success) {
        Log.w(TAG, "Response was not a true success.");
        Toast.makeText(this, R.string.RecaptchaProofActivity_failed_to_submit, Toast.LENGTH_LONG).show();
      }

      finish();
    });
  }

  private static final class TokenResult {
    final boolean clearState;
    final boolean success;

    private TokenResult(boolean clearState, boolean success) {
      this.clearState = clearState;
      this.success    = success;
    }
  }

  public static class RecaptchaProofContract extends ActivityResultContract<Void, Boolean> {

    @Override
    public @NonNull Intent createIntent(@NonNull Context context, Void unused) {
      return new Intent(context, RecaptchaProofActivity.class);
    }

    @Override
    public Boolean parseResult(int resultCode, @Nullable Intent intent) {
      return resultCode == RESULT_OK;
    }
  }
}