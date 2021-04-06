package org.thoughtcrime.securesms.payments.preferences.addmoney;

import android.net.Uri;

import androidx.annotation.NonNull;

public final class AddressAndUri {
  private final String addressB58;
  private final Uri    uri;

  AddressAndUri(@NonNull String addressB58, @NonNull Uri uri) {
    this.addressB58 = addressB58;
    this.uri        = uri;
  }

  public String getAddressB58() {
    return addressB58;
  }

  public Uri getUri() {
    return uri;
  }
}
