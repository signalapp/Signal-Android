package org.thoughtcrime.securesms;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.components.TypingIndicatorView;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

public class TypingIndicatorIntroFragment extends Fragment {

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
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View         v          = inflater.inflate(R.layout.experience_upgrade_typing_indicators_fragment, container, false);
    SwitchCompat preference = ViewUtil.findById(v, R.id.preference);

    ((TypingIndicatorView) v.findViewById(R.id.typing_indicator)).startAnimation();

    preference.setChecked(TextSecurePreferences.isTypingIndicatorsEnabled(getContext()));
    preference.setOnCheckedChangeListener((buttonView, isChecked) -> {
      TextSecurePreferences.setTypingIndicatorsEnabled(getContext(), isChecked);
      ApplicationContext.getInstance(requireContext())
                        .getJobManager()
                        .add(new MultiDeviceConfigurationUpdateJob(getContext(),
                                                                   TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                   isChecked,
                                                                   TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext())));
    });

    return v;
  }
}
