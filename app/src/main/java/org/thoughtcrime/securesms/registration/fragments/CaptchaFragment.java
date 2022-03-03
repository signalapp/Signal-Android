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
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.viewmodel.BaseRegistrationViewModel;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.io.Serializable;

/**
 * Fragment that displays a Captcha in a WebView.
 */
public final class CaptchaFragment extends LoggingFragment {

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

    webView.loadUrl(BuildConfig.SIGNAL_CAPTCHA_URL);

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

  private void handleToken(@NonNull String token) {
    viewModel.setCaptchaResponse(token);

    NavHostFragment.findNavController(this).navigateUp();
  }

  public interface CaptchaViewModelProvider extends Serializable {
    @NonNull BaseRegistrationViewModel get(@NonNull CaptchaFragment fragment);
  }
}
