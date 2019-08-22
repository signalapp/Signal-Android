package org.thoughtcrime.securesms.mms;


import android.content.Context;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.mms.service_alt.MmsConfig;

import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashMap;
import java.util.Map;

final class MmsConfigManager {

  private static final Map<Integer, MmsConfig> mmsConfigMap = new HashMap<>();

  @WorkerThread
  synchronized static @NonNull MmsConfig getMmsConfig(Context context, int subscriptionId) {
    MmsConfig mmsConfig = mmsConfigMap.get(subscriptionId);
    if (mmsConfig != null) {
      return mmsConfig;
    }

    MmsConfig loadedConfig = loadMmsConfig(context, subscriptionId);

    mmsConfigMap.put(subscriptionId, loadedConfig);

    return loadedConfig;
  }

  private static @NonNull MmsConfig loadMmsConfig(Context context, int subscriptionId) {
    Optional<SubscriptionInfoCompat> subscriptionInfo = new SubscriptionManagerCompat(context).getActiveSubscriptionInfo(subscriptionId);

    if (subscriptionInfo.isPresent()) {
      SubscriptionInfoCompat subscriptionInfoCompat = subscriptionInfo.get();
      Configuration configuration = context.getResources().getConfiguration();
      configuration.mcc = subscriptionInfoCompat.getMcc();
      configuration.mnc = subscriptionInfoCompat.getMnc();

      Context subContext = context.createConfigurationContext(configuration);
      return new MmsConfig(subContext, subscriptionId);
    }

    return new MmsConfig(context, subscriptionId);
  }

}
