package org.thoughtcrime.securesms;


import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class LinkPreviewsIntroFragment extends Fragment {

  private Controller controller;

  public static LinkPreviewsIntroFragment newInstance() {
    LinkPreviewsIntroFragment fragment = new LinkPreviewsIntroFragment();
    Bundle                    args     = new Bundle();
    fragment.setArguments(args);
    return fragment;
  }

  public LinkPreviewsIntroFragment() {}

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
    View view = inflater.inflate(R.layout.experience_upgrade_link_previews_fragment, container, false);

    view.findViewById(R.id.experience_ok_button).setOnClickListener(v -> {
      ApplicationContext.getInstance(requireContext())
                        .getJobManager()
                        .add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                   TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                                                                   TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(requireContext()),
                                                                   TextSecurePreferences.isLinkPreviewsEnabled(requireContext())));
      controller.onLinkPreviewsFinished();
    });

    return view;
  }

  public interface Controller {
    void onLinkPreviewsFinished();
  }
}
