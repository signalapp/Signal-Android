package org.thoughtcrime.securesms.components;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import org.signal.core.ui.WindowExtensionsKt;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter;
import org.thoughtcrime.securesms.util.WindowUtil;

/**
 * Base dialog fragment for rendering as a full screen dialog with animation
 * transitions.
 */
public abstract class FullScreenDialogFragment extends DialogFragment {

  protected Toolbar toolbar;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen);
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    if (dialog.getWindow() != null) {
      WindowExtensionsKt.enableEdgeToEdge(dialog.getWindow());
    }
    return dialog;
  }

  @Override
  public final @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.full_screen_dialog_fragment, container, false);
    inflater.inflate(getDialogLayoutResource(), view.findViewById(R.id.full_screen_dialog_content), true);
    toolbar = view.findViewById(R.id.full_screen_dialog_toolbar);

    if (getTitle() != -1) {
      toolbar.setTitle(getTitle());
    }

    toolbar.setNavigationOnClickListener(v -> onNavigateUp());

    SystemWindowInsetsSetter.attach(view, getViewLifecycleOwner());
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (getShowsDialog()) {
      WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
    }
  }

  protected void onNavigateUp() {
    dismissAllowingStateLoss();
  }

  protected abstract @StringRes int getTitle();

  protected abstract @LayoutRes int getDialogLayoutResource();
}
