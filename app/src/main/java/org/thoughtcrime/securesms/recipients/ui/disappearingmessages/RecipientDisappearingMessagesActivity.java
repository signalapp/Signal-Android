package org.thoughtcrime.securesms.recipients.ui.disappearingmessages;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity;
import org.thoughtcrime.securesms.components.settings.app.privacy.expire.ExpireTimerSettingsFragmentArgs;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * For select a expire timer for a recipient (individual or group).
 */
public final class RecipientDisappearingMessagesActivity extends DSLSettingsActivity {

  public static @NonNull Intent forRecipient(@NonNull Context context, @NonNull RecipientId recipientId) {
    Intent intent = new Intent(context, RecipientDisappearingMessagesActivity.class);
    intent.putExtra(DSLSettingsActivity.ARG_NAV_GRAPH, R.navigation.app_settings_expire_timer)
          .putExtra(DSLSettingsActivity.ARG_START_BUNDLE, new ExpireTimerSettingsFragmentArgs.Builder().setRecipientId(recipientId).build().toBundle());

    return intent;
  }

  public static @NonNull Intent forCreateGroup(@NonNull Context context, @Nullable Integer initialValue) {
    Intent intent = new Intent(context, RecipientDisappearingMessagesActivity.class);
    intent.putExtra(DSLSettingsActivity.ARG_NAV_GRAPH, R.navigation.app_settings_expire_timer)
          .putExtra(DSLSettingsActivity.ARG_START_BUNDLE, new ExpireTimerSettingsFragmentArgs.Builder().setForResultMode(true)
                                                                                                       .setInitialValue(initialValue)
                                                                                                       .build()
                                                                                                       .toBundle());

    return intent;
  }

}
