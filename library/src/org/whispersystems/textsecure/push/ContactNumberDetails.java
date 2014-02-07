package org.whispersystems.textsecure.push;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContactNumberDetails extends ContactDetails {
  private static final String TAG = "ContactNumberDetails";

  private String number;

  public ContactNumberDetails() { super(); }

  public ContactNumberDetails(String number) {
    super();
    this.number = number;
  }

  public ContactNumberDetails(String number, String relay) {
    super(relay);
    this.number = number;
  }

  public String getNumber() {
    return number;
  }

  public static List<ContactNumberDetails> fromContactTokenDetailsList(List<ContactTokenDetails> contactTokenDetails, final Map<String, String> tokenMap) {
    if (contactTokenDetails == null || tokenMap == null) return null;

    List<ContactNumberDetails> contactNumberDetails = new ArrayList<ContactNumberDetails>(contactTokenDetails.size());
    for (ContactTokenDetails tokenDetails : contactTokenDetails) {
      if (tokenMap.containsKey(tokenDetails.getToken()))
        contactNumberDetails.add(new ContactNumberDetails(tokenMap.get(tokenDetails.getToken()), tokenDetails.getRelay()));
      else
        Log.w(TAG, "tokenMap was missing a contact.");
    }
    return contactNumberDetails;
  }
}
