package org.whispersystems.textsecure.push;

import android.content.Context;

import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.util.InvalidNumberException;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

public class PushDestination {

  private final String e164number;
  private final String relay;

  private PushDestination(String e164number, String relay) {
    this.e164number = e164number;
    this.relay      = relay;
  }

  public String getNumber() {
    return e164number;
  }

  public String getRelay() {
    return relay;
  }

  public static PushDestination create(Context context,
                                       PushServiceSocket.PushCredentials credentials,
                                       String destinationNumber)
      throws InvalidNumberException
  {
    String e164destination = PhoneNumberFormatter.formatNumber(destinationNumber, credentials.getLocalNumber(context));
    String relay           = Directory.getInstance(context).getRelay(e164destination);

    return new PushDestination(e164destination, relay);
  }
}
