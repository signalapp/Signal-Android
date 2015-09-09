package org.thoughtcrime.redphone.signaling.signals;

public class SignalPreferenceSignal extends Signal {

  private final String preference;

  public SignalPreferenceSignal(String localNumber, String password, String preference) {
    super(localNumber, password, -1);
    this.preference = preference;
  }

  @Override
  protected String getMethod() {
    return "PUT";
  }

  @Override
  protected String getLocation() {
    return "/users/signaling_method/" + preference;
  }

  @Override
  protected String getBody() {
    return null;
  }

}
