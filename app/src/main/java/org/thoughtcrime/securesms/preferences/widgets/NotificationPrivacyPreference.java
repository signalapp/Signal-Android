package org.thoughtcrime.securesms.preferences.widgets;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class NotificationPrivacyPreference {

  private final String preference;

  public NotificationPrivacyPreference(String preference) {
    this.preference = preference;
  }

  public boolean isDisplayContact() {
    return "all".equals(preference) || "contact".equals(preference);
  }

  public boolean isDisplayMessage() {
    return "all".equals(preference);
  }

  public boolean isDisplayNothing() {
    return !isDisplayContact();
  }

  @Override
  public @NonNull String toString() {
    return preference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final NotificationPrivacyPreference that = (NotificationPrivacyPreference) o;
    return Objects.equals(preference, that.preference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(preference);
  }
}
