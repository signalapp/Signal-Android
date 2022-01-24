package org.thoughtcrime.securesms.qr;

import androidx.annotation.NonNull;

public interface ScanListener {
  void onQrDataFound(@NonNull String data);
}
