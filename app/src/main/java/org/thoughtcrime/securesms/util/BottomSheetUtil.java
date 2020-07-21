package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public final class BottomSheetUtil {

  public static final String STANDARD_BOTTOM_SHEET_FRAGMENT_TAG = "BOTTOM";

  private BottomSheetUtil() {}

  /**
   * Show preventing a possible IllegalStateException.
   */
  public static void show(@NonNull FragmentManager manager,
                          @Nullable String tag,
                          @NonNull BottomSheetDialogFragment dialog)
  {
    FragmentTransaction transaction = manager.beginTransaction();
    transaction.add(dialog, tag);
    transaction.commitAllowingStateLoss();
  }
}
