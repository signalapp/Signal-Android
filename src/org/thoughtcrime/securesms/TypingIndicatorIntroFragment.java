package org.thoughtcrime.securesms;


import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.components.TypingIndicatorView;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class TypingIndicatorIntroFragment extends Fragment {

  private Controller controller;

  public static TypingIndicatorIntroFragment newInstance() {
    TypingIndicatorIntroFragment fragment = new TypingIndicatorIntroFragment();
    Bundle                       args     = new Bundle();
    fragment.setArguments(args);
    return fragment;
  }

  public TypingIndicatorIntroFragment() {}

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);

    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement the Controller interface.");
    }

    controller = (Controller) getActivity();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view      = inflater.inflate(R.layout.experience_upgrade_typing_indicators_fragment, container, false);
    View yesButton = view.findViewById(R.id.experience_yes_button);
    View noButton  = view.findViewById(R.id.experience_no_button);

    ((TypingIndicatorView) view.findViewById(R.id.typing_indicator)).startAnimation();

    yesButton.setOnClickListener(v -> onButtonClicked(true));
    noButton.setOnClickListener(v -> onButtonClicked(false));

    return view;
  }

  private void onButtonClicked(boolean typingEnabled) {
    TextSecurePreferences.setTypingIndicatorsEnabled(getContext(), typingEnabled);
    ApplicationContext.getInstance(requireContext())
                      .getJobManager()
                      .add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                 typingEnabled,
                                                                 TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                                                                 TextSecurePreferences.isLinkPreviewsEnabled(getContext())));

    controller.onTypingIndicatorsFinished();
  }

  public interface Controller {
    void onTypingIndicatorsFinished();
  }
}
