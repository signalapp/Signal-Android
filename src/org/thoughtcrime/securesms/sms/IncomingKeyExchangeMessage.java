package org.thoughtcrime.securesms.sms;

public class IncomingKeyExchangeMessage extends IncomingTextMessage {

  private boolean isStale;
  private boolean isProcessed;

  IncomingKeyExchangeMessage(IncomingTextMessage base, String newBody) {
    super(base, newBody);

    if (base instanceof IncomingKeyExchangeMessage) {
      this.isStale     = ((IncomingKeyExchangeMessage)base).isStale;
      this.isProcessed = ((IncomingKeyExchangeMessage)base).isProcessed;
    }
  }

  @Override
  public IncomingTextMessage withMessageBody(String messageBody) {
    return new IncomingKeyExchangeMessage(this, messageBody);
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

  @Override
  public boolean isKeyExchange() {
    return true;
  }
}
