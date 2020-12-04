package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

public final class CertificateValues extends SignalStoreValues {

  private static final String UD_CERTIFICATE_UUID_AND_E164 = "certificate.uuidAndE164";
  private static final String UD_CERTIFICATE_UUID_ONLY     = "certificate.uuidOnly";

  CertificateValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @WorkerThread
  public void setUnidentifiedAccessCertificate(@NonNull CertificateType certificateType,
                                               @Nullable byte[] certificate)
  {
    KeyValueStore.Writer writer = getStore().beginWrite();

    switch (certificateType) {
      case UUID_AND_E164: writer.putBlob(UD_CERTIFICATE_UUID_AND_E164, certificate); break;
      case UUID_ONLY    : writer.putBlob(UD_CERTIFICATE_UUID_ONLY, certificate);     break;
      default           : throw new AssertionError();
    }

    writer.commit();
  }

  public @Nullable byte[] getUnidentifiedAccessCertificate(@NonNull CertificateType certificateType) {
    switch (certificateType) {
      case UUID_AND_E164: return getBlob(UD_CERTIFICATE_UUID_AND_E164, null);
      case UUID_ONLY    : return getBlob(UD_CERTIFICATE_UUID_ONLY, null);
      default           : throw new AssertionError();
    }
  }

}
