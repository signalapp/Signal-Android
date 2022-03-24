package org.whispersystems.signalservice.api.profiles;

import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;

import java.util.Optional;


public final class ProfileAndCredential {

  private final SignalServiceProfile             profile;
  private final SignalServiceProfile.RequestType requestType;
  private final Optional<ProfileKeyCredential>   profileKeyCredential;

  public ProfileAndCredential(SignalServiceProfile profile,
                              SignalServiceProfile.RequestType requestType,
                              Optional<ProfileKeyCredential> profileKeyCredential)
  {
    this.profile              = profile;
    this.requestType          = requestType;
    this.profileKeyCredential = profileKeyCredential;
  }

  public SignalServiceProfile getProfile() {
    return profile;
  }

  public SignalServiceProfile.RequestType getRequestType() {
    return requestType;
  }

  public Optional<ProfileKeyCredential> getProfileKeyCredential() {
    return profileKeyCredential;
  }
}
