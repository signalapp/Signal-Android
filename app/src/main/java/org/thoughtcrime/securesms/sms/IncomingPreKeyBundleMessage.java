package org.thoughtcrime.securesms.sms;

public class IncomingPreKeyBundleMessage extends IncomingTextMessage {

  private final boolean legacy;

  public IncomingPreKeyBundleMessage(IncomingTextMessage base, String newBody, boolean legacy) {
    super(base, newBody);
    this.legacy = legacy;
  }

    @Override
  public boolean isLegacyPreKeyBundle() {
    return legacy;
  }

  @Override
  public boolean isContentPreKeyBundle() {
    return !legacy;
  }

}
