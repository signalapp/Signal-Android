package org.thoughtcrime.securesms.util.dualsim;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SubscriptionManagerCompat {

  private static final String TAG = Log.tag(SubscriptionManagerCompat.class);

  private final Context context;

  public SubscriptionManagerCompat(Context context) {
    this.context = context.getApplicationContext();
  }

  public Optional<Integer> getPreferredSubscriptionId() {
    if (Build.VERSION.SDK_INT < 24) {
      return Optional.absent();
    }

    return Optional.of(SubscriptionManager.getDefaultSmsSubscriptionId());
  }

  public Optional<SubscriptionInfoCompat> getActiveSubscriptionInfo(int subscriptionId) {
    if (Build.VERSION.SDK_INT < 22) {
      return Optional.absent();
    }

    return Optional.fromNullable(getActiveSubscriptionInfoMap(false).get(subscriptionId));
  }

  public @NonNull Collection<SubscriptionInfoCompat> getActiveAndReadySubscriptionInfos() {
    if (Build.VERSION.SDK_INT < 22) {
      return Collections.emptyList();
    }

    return getActiveSubscriptionInfoMap(true).values();
  }

  @RequiresApi(api = 22)
  private @NonNull Map<Integer, SubscriptionInfoCompat> getActiveSubscriptionInfoMap(boolean excludeUnreadySubscriptions) {
    List<SubscriptionInfo> subscriptionInfos = getActiveSubscriptionInfoList();

    if (subscriptionInfos.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<SubscriptionInfo, CharSequence>  descriptions = getDescriptionsFor(subscriptionInfos);
    Map<Integer, SubscriptionInfoCompat> map          = new LinkedHashMap<>();

    for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
      if (!excludeUnreadySubscriptions || isReady(subscriptionInfo)) {
        map.put(subscriptionInfo.getSubscriptionId(),
                new SubscriptionInfoCompat(subscriptionInfo.getSubscriptionId(),
                                           descriptions.get(subscriptionInfo),
                                           subscriptionInfo.getMcc(),
                                           subscriptionInfo.getMnc()));
      }
    }

    return map;
  }

  public boolean isMultiSim() {
    if (Build.VERSION.SDK_INT < 22) {
      return false;
    }

    return getActiveSubscriptionInfoList().size() >= 2;
  }

  @RequiresApi(api = 22)
  private @NonNull List<SubscriptionInfo> getActiveSubscriptionInfoList() {
    SubscriptionManager subscriptionManager = ServiceUtil.getSubscriptionManager(context);

    if (subscriptionManager == null) {
      Log.w(TAG, "Missing SubscriptionManager.");
      return Collections.emptyList();
    }

    List<SubscriptionInfo> list = subscriptionManager.getActiveSubscriptionInfoList();

    return list != null? list : Collections.emptyList();
  }

  @RequiresApi(api = 22)
  private Map<SubscriptionInfo, CharSequence> getDescriptionsFor(@NonNull Collection<SubscriptionInfo> subscriptions) {
    Map<SubscriptionInfo, CharSequence> descriptions;

    descriptions = createDescriptionMap(subscriptions, SubscriptionInfo::getDisplayName);
    if (hasNoDuplicates(descriptions.values())) return descriptions;

    return createDescriptionMap(subscriptions, this::describeSimIndex);
  }

  @RequiresApi(api = 22)
  private String describeSimIndex(SubscriptionInfo info) {
    return context.getString(R.string.conversation_activity__sim_n, info.getSimSlotIndex() + 1);
  }

  private static Map<SubscriptionInfo, CharSequence> createDescriptionMap(@NonNull Collection<SubscriptionInfo> subscriptions,
                                                                          @NonNull Function<SubscriptionInfo, CharSequence> createDescription)
  {
    Map<SubscriptionInfo, CharSequence> descriptions = new HashMap<>();
    for (SubscriptionInfo subscriptionInfo: subscriptions) {
      descriptions.put(subscriptionInfo, createDescription.apply(subscriptionInfo));
    }
    return descriptions;
  }

  private static <T> boolean hasNoDuplicates(Collection<T> collection) {
    final Set<T> set = new HashSet<>();

    for (T t : collection) {
      if (!set.add(t)) {
        return false;
      }
    }
    return true;
  }

  private boolean isReady(@NonNull SubscriptionInfo subscriptionInfo) {
    if (Build.VERSION.SDK_INT < 24) return true;

    TelephonyManager telephonyManager = ServiceUtil.getTelephonyManager(context);

    TelephonyManager specificTelephonyManager = telephonyManager.createForSubscriptionId(subscriptionInfo.getSubscriptionId());

    return specificTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
  }
}
