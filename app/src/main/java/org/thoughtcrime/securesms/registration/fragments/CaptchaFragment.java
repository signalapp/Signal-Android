package org.thoughtcrime.securesms.registration.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.viewmodel.BaseRegistrationViewModel;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.io.Serializable;

/**
 * Fragment that displays a Captcha in a WebView.
 */
public final class CaptchaFragment extends LoggingFragment {
  private static final String TAG = Log.tag(CaptchaFragment.class);
  private WebView mWebview;
  private View mMouseCursor;
  public static final String EXTRA_VIEW_MODEL_PROVIDER = "view_model_provider";

  private BaseRegistrationViewModel viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_captcha, container, false);
  }

  @Override
  @SuppressLint("SetJavaScriptEnabled")
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mWebview = view.findViewById(R.id.registration_captcha_web_view);
    mMouseCursor = view.findViewById(R.id.mouse_cursor);

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
      public void onPageFinished(WebView view, String url) {
//        view.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
//          SystemClock.uptimeMillis(),MotionEvent.ACTION_DOWN,160, 120, 0));
//        view.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
//          SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,160, 120, 0));
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        switch (event.getKeyCode()) {
          case KeyEvent.KEYCODE_DPAD_CENTER:
            view.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), event.getAction(), mMouseCursor.getX(), mMouseCursor.getY(), 0));
            return;
        }
        super.onUnhandledKeyEvent(view, event);
      }

    });

    mWebview.loadUrl(RegistrationConstants.SIGNAL_CAPTCHA_URL);

    CaptchaViewModelProvider provider = null;
    if (getArguments() != null) {
      provider = (CaptchaViewModelProvider) requireArguments().getSerializable(EXTRA_VIEW_MODEL_PROVIDER);
    }

    if (provider == null) {
      viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);
    } else {
      viewModel = provider.get(this);
    }
  }

  private static final float MCURSOR_MARGIN_X = 4;
  private static final float MCURSOR_MARGIN_Y = 7;
  private static final float MCURSOR_MOVE = 7;
  private static final int VIEW_SCROLL_STEP = 10;

  public void onKeyDown(int keyCode, int action) {

    if (mWebview == null) {
      return;
    }

    float x = mMouseCursor.getX();
    float y = mMouseCursor.getY();

    switch (keyCode) {
      case KeyEvent.KEYCODE_0:
        mWebview.reload();
        break;
      case KeyEvent.KEYCODE_2:
//        mWebview.dispatchKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_DPAD_UP));
        if (action == KeyEvent.ACTION_UP) break;
        y -= MCURSOR_MOVE;
        if (y + MCURSOR_MARGIN_Y < 0) {
          mWebview.scrollBy(0, -VIEW_SCROLL_STEP);
          mMouseCursor.setY(-MCURSOR_MARGIN_Y);
        } else {
          mMouseCursor.setY(y);
        }
        break;
      case KeyEvent.KEYCODE_4:
//        mWebview.dispatchKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_DPAD_LEFT));
        if (action == KeyEvent.ACTION_UP) break;
        x -= MCURSOR_MOVE;
        mMouseCursor.setX(x + MCURSOR_MARGIN_X < 0 ? -MCURSOR_MARGIN_X : x);
        break;
      case KeyEvent.KEYCODE_6:
//        mWebview.dispatchKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_DPAD_RIGHT));
        if (action == KeyEvent.ACTION_UP) break;
        x += MCURSOR_MOVE;
        mMouseCursor.setX(x + MCURSOR_MARGIN_X > 320 - 20 ? 320 - MCURSOR_MARGIN_X - 20 : x);
        break;
      case KeyEvent.KEYCODE_8:
//        mWebview.dispatchKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_DPAD_DOWN));
        if (action == KeyEvent.ACTION_UP) break;
        y += MCURSOR_MOVE;
        if (y > 240 - MCURSOR_MARGIN_Y - 20) {
          mWebview.scrollBy(0, VIEW_SCROLL_STEP);
          mMouseCursor.setY(240 - MCURSOR_MARGIN_Y - 20);
        } else {
          mMouseCursor.setY(y);
        }
        break;
//      case KeyEvent.KEYCODE_DPAD_UP:
//        if (action == KeyEvent.ACTION_UP) break;
//        mWebview.scrollBy(0, -4*VIEW_SCROLL_STEP);
//        break;
//      case KeyEvent.KEYCODE_DPAD_DOWN:
//        if (action == KeyEvent.ACTION_UP) break;
//        mWebview.scrollBy(0, 4*VIEW_SCROLL_STEP);
//        break;
      case KeyEvent.KEYCODE_5:
//      case KeyEvent.KEYCODE_DPAD_CENTER:
          mWebview.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
                  SystemClock.uptimeMillis(), action, mMouseCursor.getX()+MCURSOR_MARGIN_X, mMouseCursor.getY()+MCURSOR_MARGIN_Y, 0));
//        if (action == KeyEvent.ACTION_DOWN) {
//           mWebview.dispatchKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_SPACE));
//        }
//        mWebview.dispatchKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER));
        break;
      case KeyEvent.KEYCODE_APOSTROPHE:
        if (mWebview.canGoBack())
          mWebview.goBack();
        break;
    }
  }

  private void handleToken(@NonNull String token) {
    viewModel.setCaptchaResponse(token);
    NavHostFragment.findNavController(this).navigateUp();
  }
  public interface CaptchaViewModelProvider extends Serializable {
    @NonNull BaseRegistrationViewModel get(@NonNull CaptchaFragment fragment);
  }
}
