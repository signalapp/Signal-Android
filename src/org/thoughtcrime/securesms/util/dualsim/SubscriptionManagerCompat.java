package org.thoughtcrime.securesms.util.dualsim;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import org.thoughtcrime.securesms.util.ServiceUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public final class SubscriptionManagerCompat {

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

    SubscriptionInfo subscriptionInfo = getSubscriptionManager().getActiveSubscriptionInfo(subscriptionId);

    if (subscriptionInfo != null) {
      return Optional.of(new SubscriptionInfoCompat(subscriptionId, subscriptionInfo.getDisplayName(),
                                                    subscriptionInfo.getMcc(), subscriptionInfo.getMnc()));
    } else {
      return Optional.absent();
    }
  }

  public @NonNull List<SubscriptionInfoCompat> getActiveSubscriptionInfoList() {
    if (Build.VERSION.SDK_INT < 22) {
      return new LinkedList<>();
    }

    List<SubscriptionInfo> subscriptionInfos = getSubscriptionManager().getActiveSubscriptionInfoList();

    if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
      return new LinkedList<>();
    }

    List<SubscriptionInfoCompat> compatList = new LinkedList<>();

    for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
      if (isReady(subscriptionInfo)) {
        compatList.add(new SubscriptionInfoCompat(subscriptionInfo.getSubscriptionId(),
                                                  subscriptionInfo.getDisplayName(),
                                                  subscriptionInfo.getMcc(),
                                                  subscriptionInfo.getMnc()));
      }
    }

    return compatList;
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  private SubscriptionManager getSubscriptionManager() {
    return ServiceUtil.getSubscriptionManager(context);
  }

  private boolean isReady(@NonNull SubscriptionInfo subscriptionInfo) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true;

    TelephonyManager telephonyManager = ServiceUtil.getTelephonyManager(context);

    TelephonyManager specificTelephonyManager = telephonyManager.createForSubscriptionId(subscriptionInfo.getSubscriptionId());

    return specificTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
  }
}
