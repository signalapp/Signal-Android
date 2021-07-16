package org.thoughtcrime.securesms.registration.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

/**
 * Fragment that displays a Captcha in a WebView.
 */
public final class CaptchaFragment extends BaseRegistrationFragment {
  private static final String TAG = Log.tag(CaptchaFragment.class);
  private WebView mWebview;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_captcha, container, false);
  }

  @Override
  @SuppressLint("SetJavaScriptEnabled")
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mWebview = view.findViewById(R.id.registration_captcha_web_view);

    mWebview.getSettings().setJavaScriptEnabled(true);
    mWebview.clearCache(true);

    mWebview.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url != null && url.startsWith(RegistrationConstants.SIGNAL_CAPTCHA_SCHEME)) {
          handleToken(url.substring(RegistrationConstants.SIGNAL_CAPTCHA_SCHEME.length()));
          return true;
        }
        return false;
      }
    });

    mWebview.loadUrl(RegistrationConstants.SIGNAL_CAPTCHA_URL);
  }

  public void onKeyDown(int keyCode) {
    if (mWebview == null) {
      return;
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_0:
        Log.d(TAG,"reload");
        mWebview.reload();
        break;
      case KeyEvent.KEYCODE_2:
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP));
        break;
      case KeyEvent.KEYCODE_4:
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));
        break;
      case KeyEvent.KEYCODE_6:
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
        break;
      case KeyEvent.KEYCODE_8:
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));
        break;
      case KeyEvent.KEYCODE_5:
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        mWebview.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        break;
    }
  }

  private void handleToken(@NonNull String token) {
    getModel().onCaptchaResponse(token);

    Navigation.findNavController(requireView()).navigate(CaptchaFragmentDirections.actionCaptchaComplete());
  }
}
