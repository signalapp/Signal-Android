package org.thoughtcrime.securesms.sms;

public class IncomingKeyExchangeMessage extends IncomingTextMessage {

  private boolean isStale;
  private boolean isProcessed;
  private boolean isCorrupted;
  private boolean isInvalidVersion;
  private boolean isLegacyVersion;

  public IncomingKeyExchangeMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);

    if (base instanceof IncomingKeyExchangeMessage) {
      this.isStale          = ((IncomingKeyExchangeMessage)base).isStale;
      this.isProcessed      = ((IncomingKeyExchangeMessage)base).isProcessed;
      this.isCorrupted      = ((IncomingKeyExchangeMessage)base).isCorrupted;
      this.isInvalidVersion = ((IncomingKeyExchangeMessage)base).isInvalidVersion;
      this.isLegacyVersion  = ((IncomingKeyExchangeMessage)base).isLegacyVersion;
    }
  }

  @Override
  public IncomingTextMessage withMessageBody(String messageBody) {
    return new IncomingKeyExchangeMessage(this, messageBody);
  }

  public boolean isIdentityUpdate() {
    return false;
  }

  public boolean isStale() {
    return isStale;
  }

  public boolean isProcessed() {
    return isProcessed;
  }

  public void setStale(boolean isStale) {
    this.isStale = isStale;
  }

  public void setProcessed(boolean isProcessed) {
    this.isProcessed = isProcessed;
  }

  public boolean isCorrupted() {
    return isCorrupted;
  }

  public void setCorrupted(boolean isCorrupted) {
    this.isCorrupted = isCorrupted;
  }

  public boolean isInvalidVersion() {
    return isInvalidVersion;
  }

  public void setInvalidVersion(boolean isInvalidVersion) {
    this.isInvalidVersion = isInvalidVersion;
  }

  public boolean isLegacyVersion() {
    return isLegacyVersion;
  }

  public void setLegacyVersion(boolean isLegacyVersion) {
    this.isLegacyVersion = isLegacyVersion;
  }

  @Override
  public boolean isKeyExchange() {
    return true;
  }
}
