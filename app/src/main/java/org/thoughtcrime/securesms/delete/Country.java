package org.thoughtcrime.securesms.delete;

import androidx.annotation.NonNull;

import java.util.Objects;

final class Country {
  private final String displayName;
  private final int    code;
  private final String normalized;
  private final String region;

  Country(@NonNull String displayName, int code, @NonNull String region) {
    this.displayName = displayName;
    this.code        = code;
    this.normalized  = displayName.toLowerCase();
    this.region      = region;
  }

  int getCode() {
    return code;
  }

  @NonNull String getDisplayName() {
    return displayName;
  }

  public String getNormalizedDisplayName() {
    return normalized;
  }

  @NonNull String getRegion() {
    return region;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Country country = (Country) o;
    return displayName.equals(country.displayName) &&
           code == country.code;
  }

  @Override
  public int hashCode() {
    return Objects.hash(displayName, code);
  }
}
