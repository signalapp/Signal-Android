package org.thoughtcrime.securesms.experienceupgrades;


import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.TypingIndicatorView;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class StickersIntroFragment extends Fragment {

  private Controller controller;

  public static StickersIntroFragment newInstance() {
    return new StickersIntroFragment();
  }

  public StickersIntroFragment() {}

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
    View view     = inflater.inflate(R.layout.experience_upgrade_stickers_fragment, container, false);
    View goButton = view.findViewById(R.id.stickers_experience_go_button);

    ((LottieAnimationView) view.findViewById(R.id.stickers_experience_animation)).playAnimation();

    goButton.setOnClickListener(v -> controller.onStickersFinished());

    return view;
  }

  public interface Controller {
    void onStickersFinished();
  }
}
