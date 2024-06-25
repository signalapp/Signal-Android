package org.thoughtcrime.securesms.groups.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.RemoteConfig;

public final class GroupLimitDialog {

  public static void showHardLimitMessage(@NonNull Context context) {
    new MaterialAlertDialogBuilder(context)
                   .setTitle(R.string.ContactSelectionListFragment_maximum_group_size_reached)
                   .setMessage(context.getString(R.string.ContactSelectionListFragment_signal_groups_can_have_a_maximum_of_d_members, RemoteConfig.groupLimits().getHardLimit()))
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }

  public static void showRecommendedLimitMessage(@NonNull Context context) {
    new MaterialAlertDialogBuilder(context)
                   .setTitle(R.string.ContactSelectionListFragment_recommended_member_limit_reached)
                   .setMessage(context.getString(R.string.ContactSelectionListFragment_signal_groups_perform_best_with_d_members_or_fewer, RemoteConfig.groupLimits().getRecommendedLimit()))
                   .setPositiveButton(android.R.string.ok, null)
                   .show();
  }
}
