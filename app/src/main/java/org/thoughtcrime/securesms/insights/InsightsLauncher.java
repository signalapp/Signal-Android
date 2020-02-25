package org.thoughtcrime.securesms.insights;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public final class InsightsLauncher {

  private static final String MODAL_TAG = "modal.fragment";

  public static void showInsightsModal(@NonNull Context context, @NonNull FragmentManager fragmentManager) {
    if (InsightsOptOut.userHasOptedOut(context)) return;

    final Fragment fragment = fragmentManager.findFragmentByTag(MODAL_TAG);

    if (fragment == null) new InsightsModalDialogFragment().show(fragmentManager, MODAL_TAG);
  }

  public static void showInsightsDashboard(@NonNull FragmentManager fragmentManager) {
    new InsightsDashboardDialogFragment().show(fragmentManager, null);
  }

}
