package org.thoughtcrime.securesms.util.dualsim;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class SubscriptionManagerCompat {

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

    SubscriptionInfo subscriptionInfo = SubscriptionManager.from(context).getActiveSubscriptionInfo(subscriptionId);

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

    List<SubscriptionInfo> subscriptionInfos = SubscriptionManager.from(context).getActiveSubscriptionInfoList();

    if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
      return new LinkedList<>();
    }

    List<SubscriptionInfoCompat> compatList = new LinkedList<>();

    for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
      compatList.add(new SubscriptionInfoCompat(subscriptionInfo.getSubscriptionId(),
                                                subscriptionInfo.getDisplayName(),
                                                subscriptionInfo.getMcc(),
                                                subscriptionInfo.getMnc()));
    }

    return compatList;
  }

}
