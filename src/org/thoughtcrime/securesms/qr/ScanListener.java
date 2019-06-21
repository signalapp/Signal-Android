package org.thoughtcrime.securesms.qr;

public interface ScanListener {
  void onQrDataFound(String data);
}
