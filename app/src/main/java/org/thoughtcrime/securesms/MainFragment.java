package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class MainFragment extends Fragment {

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (!(requireActivity() instanceof MainActivity)) {
      throw new IllegalStateException("Can only be used inside of MainActivity!");
    }
  }

  protected @NonNull MainNavigator getNavigator() {
    return MainNavigator.get(requireActivity());
  }
}
