package org.privatechats.redphone.crypto.zrtp;

import android.util.Log;

public class RedPhoneClientId {

  private boolean isRedphoneClient;
  private int     clientIdInteger;

  public RedPhoneClientId(String clientId) {
    String[] clientIdParts = clientId.split(" ");

    if (clientIdParts.length < 2) {
      isRedphoneClient = false;
      return;
    }

    if (!"RedPhone".equals(clientIdParts[0].trim())) {
      isRedphoneClient = false;
      return;
    }

    try {
      this.clientIdInteger = Integer.parseInt(clientIdParts[1]);
    } catch (NumberFormatException nfe) {
      Log.w("RedPhoneClientId", nfe);
      this.isRedphoneClient = false;
    }

    this.isRedphoneClient = true;
  }

  public boolean isImplicitDh3kVersion() {
    return this.isRedphoneClient && (this.clientIdInteger == 19 || this.clientIdInteger == 24);
  }

  public boolean isLegacyConfirmConnectionVersion() {
    return this.isRedphoneClient && this.clientIdInteger < 24;
  }
}
