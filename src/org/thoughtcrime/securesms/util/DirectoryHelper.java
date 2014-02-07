package org.thoughtcrime.securesms.util;

import android.content.Context;

import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.push.ContactNumberDetails;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.DirectoryUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirectoryHelper {

  public static void refreshDirectory(final Context context) {
    refreshDirectory(context, PushServiceSocketFactory.create(context));
  }

  public static void refreshDirectory(final Context context, final PushServiceSocket socket) {
    refreshDirectory(context, socket, TextSecurePreferences.getLocalNumber(context));
  }

  public static void refreshDirectory(final Context context, final PushServiceSocket socket, final String localNumber) {
    final Directory         directory = Directory.getInstance(context);

    final Set<String> eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);

    final Map<String, String>        tokenMap      = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
    final List<ContactTokenDetails>  activeTokens  = socket.retrieveDirectory(tokenMap.keySet());

    if (activeTokens != null) {
      final List<ContactNumberDetails> activeNumbers = ContactNumberDetails.fromContactTokenDetailsList(activeTokens, tokenMap);
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(tokenMap.get(activeToken.getToken()));
      }

      directory.setNumbers(activeNumbers, eligibleContactNumbers);
    }
  }
}
