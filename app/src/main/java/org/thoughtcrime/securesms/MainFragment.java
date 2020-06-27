package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;

public class MainFragment extends LoggingFragment {

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
