package org.thoughtcrime.securesms.megaphone;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public interface MegaphoneActionController {
  /**
   * When a megaphone wants to navigate to a specific intent.
   */
  void onMegaphoneNavigationRequested(@NonNull Intent intent);

  /**
   * When a megaphone wants to navigate to a specific intent for a request code.
   */
  void onMegaphoneNavigationRequested(@NonNull Intent intent, int requestCode);

  /**
   * When a megaphone wants to show a toast/snackbar.
   */
  void onMegaphoneToastRequested(@NonNull String string);

  /**
   * When a megaphone needs a raw activity reference. Favor more specific methods when possible.
   */
  @NonNull Activity getMegaphoneActivity();

  /**
   * When a megaphone has been snoozed via "remind me later" or a similar option.
   */
  void onMegaphoneSnooze(@NonNull Megaphones.Event event);

  /**
   * Called when a megaphone completed its goal.
   */
  void onMegaphoneCompleted(@NonNull Megaphones.Event event);

  /**
   * When a megaphone wnats to show a dialog fragment.
   */
  void onMegaphoneDialogFragmentRequested(@NonNull DialogFragment dialogFragment);
}
