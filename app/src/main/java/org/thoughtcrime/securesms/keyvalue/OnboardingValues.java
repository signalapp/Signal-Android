package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.util.Util;

public final class OnboardingValues extends SignalStoreValues {

  private static final String SHOW_NEW_GROUP      = "onboarding.new_group";
  private static final String SHOW_INVITE_FRIENDS = "onboarding.invite_friends";
  private static final String SHOW_SMS            = "onboarding.sms";

  OnboardingValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    putBoolean(SHOW_NEW_GROUP, true);
    putBoolean(SHOW_INVITE_FRIENDS, true);
    putBoolean(SHOW_SMS, true);
  }

  public void clearAll() {
    setShowNewGroup(false);
    setShowInviteFriends(false);
    setShowSms(false);
  }

  public boolean hasOnboarding(@NonNull Context context) {
    return shouldShowNewGroup()      ||
           shouldShowInviteFriends() ||
           shouldShowSms(context);
  }

  public void setShowNewGroup(boolean value) {
    putBoolean(SHOW_NEW_GROUP, value);
  }

  public boolean shouldShowNewGroup() {
    return getBoolean(SHOW_NEW_GROUP, false);
  }

  public void setShowInviteFriends(boolean value) {
    putBoolean(SHOW_INVITE_FRIENDS, value);
  }

  public boolean shouldShowInviteFriends() {
    return getBoolean(SHOW_INVITE_FRIENDS, false);
  }

  public void setShowSms(boolean value) {
    putBoolean(SHOW_SMS, value);
  }

  public boolean shouldShowSms(@NonNull Context context) {
    return getBoolean(SHOW_SMS, false) && !Util.isDefaultSmsProvider(context) && PhoneNumberFormatter.getLocalCountryCode() != 91;
  }
}
