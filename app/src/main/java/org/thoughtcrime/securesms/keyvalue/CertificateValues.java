package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Collections;
import java.util.List;

public final class CertificateValues extends SignalStoreValues {

  private static final String SEALED_SENDER_CERT_ACI_AND_E164 = "certificate.uuidAndE164";
  private static final String SEALED_SENDER_CERT_ACI_ONLY     = "certificate.uuidOnly";

  CertificateValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  @WorkerThread
  public void setUnidentifiedAccessCertificate(@NonNull CertificateType certificateType,
                                               @Nullable byte[] certificate)
  {
    KeyValueStore.Writer writer = getStore().beginWrite();

    switch (certificateType) {
      case ACI_AND_E164: writer.putBlob(SEALED_SENDER_CERT_ACI_AND_E164, certificate); break;
      case ACI_ONLY    : writer.putBlob(SEALED_SENDER_CERT_ACI_ONLY, certificate);     break;
      default          : throw new AssertionError();
    }

    writer.commit();
  }

  public @Nullable byte[] getUnidentifiedAccessCertificate(@NonNull CertificateType certificateType) {
    switch (certificateType) {
      case ACI_AND_E164: return getBlob(SEALED_SENDER_CERT_ACI_AND_E164, null);
      case ACI_ONLY    : return getBlob(SEALED_SENDER_CERT_ACI_ONLY, null);
      default          : throw new AssertionError();
    }
  }

}
