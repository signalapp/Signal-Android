package org.thoughtcrime.securesms;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Used in our {@link BuildConfig} to tie together the various attributes of a KBS instance. This
 * is sitting in the root directory so it can be accessed by the build config.
 */
public final class KbsEnclave {

  private final String enclaveName;
  private final String serviceId;
  private final String mrEnclave;

  public KbsEnclave(@NonNull String enclaveName, @NonNull String serviceId, @NonNull String mrEnclave) {
    this.enclaveName = enclaveName;
    this.serviceId   = serviceId;
    this.mrEnclave   = mrEnclave;
  }

  public @NonNull String getMrEnclave() {
    return mrEnclave;
  }

  public @NonNull String getEnclaveName() {
    return enclaveName;
  }

  public @NonNull String getServiceId() {
    return serviceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    KbsEnclave enclave = (KbsEnclave) o;
    return enclaveName.equals(enclave.enclaveName) &&
           serviceId.equals(enclave.serviceId)     &&
           mrEnclave.equals(enclave.mrEnclave);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enclaveName, serviceId, mrEnclave);
  }
}
