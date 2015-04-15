package org.whispersystems.textsecure.internal.push;

import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;

import java.util.List;

public class TextSecureEnvelopeEntityList {

  private List<TextSecureEnvelopeEntity> messages;

  public TextSecureEnvelopeEntityList() {}

  public List<TextSecureEnvelopeEntity> getMessages() {
    return messages;
  }
}
