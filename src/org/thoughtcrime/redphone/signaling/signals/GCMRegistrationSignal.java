package org.thoughtcrime.redphone.signaling.signals;

public class GCMRegistrationSignal extends Signal {

  private final String registrationId;

  public GCMRegistrationSignal(String localNumber, String password, String registrationId) {
    super(localNumber, password, -1);
    this.registrationId = registrationId;
  }

  @Override
  protected String getMethod() {
    return "PUT";
  }

  @Override
  protected String getLocation() {
    return "/gcm/" + registrationId;
  }

  @Override
  protected String getBody() {
    return null;
  }
}
