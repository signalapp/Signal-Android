package org.whispersystems.signalservice.api.profiles;

import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;

import java.util.Optional;


public final class ProfileAndCredential {

  private final SignalServiceProfile                   profile;
  private final SignalServiceProfile.RequestType       requestType;
  private final Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential;

  public ProfileAndCredential(SignalServiceProfile profile,
                              SignalServiceProfile.RequestType requestType,
                              Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential)
  {
    this.profile                      = profile;
    this.requestType                  = requestType;
    this.expiringProfileKeyCredential = expiringProfileKeyCredential;
  }

  public SignalServiceProfile getProfile() {
    return profile;
  }

  public SignalServiceProfile.RequestType getRequestType() {
    return requestType;
  }

  public Optional<ExpiringProfileKeyCredential> getExpiringProfileKeyCredential() {
    return expiringProfileKeyCredential;
  }
}
