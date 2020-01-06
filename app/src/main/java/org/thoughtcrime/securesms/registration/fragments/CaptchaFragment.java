package org.thoughtcrime.securesms.registration.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;

/**
 * Fragment that displays a Captcha in a WebView.
 */
public final class CaptchaFragment extends BaseRegistrationFragment {

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_captcha, container, false);
  }

  @Override
  @SuppressLint("SetJavaScriptEnabled")
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    WebView webView = view.findViewById(R.id.registration_captcha_web_view);

    webView.getSettings().setJavaScriptEnabled(true);
    webView.clearCache(true);

    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url != null && url.startsWith(RegistrationConstants.SIGNAL_CAPTCHA_SCHEME)) {
          handleToken(url.substring(RegistrationConstants.SIGNAL_CAPTCHA_SCHEME.length()));
          return true;
        }
        return false;
      }
    });

    webView.loadUrl(RegistrationConstants.SIGNAL_CAPTCHA_URL);
  }

  private void handleToken(@NonNull String token) {
    getModel().onCaptchaResponse(token);

    Navigation.findNavController(requireView()).navigate(CaptchaFragmentDirections.actionCaptchaComplete());
  }
}
