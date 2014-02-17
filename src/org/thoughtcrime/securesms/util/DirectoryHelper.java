package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.whispersystems.textsecure.directory.Directory;
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
    Directory                 directory              = Directory.getInstance(context);
    Set<String>               eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
    Map<String, String>       tokenMap               = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
    List<ContactTokenDetails> activeTokens           = socket.retrieveDirectory(tokenMap.keySet());


    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(tokenMap.get(activeToken.getToken()));
        activeToken.setNumber(tokenMap.get(activeToken.getToken()));
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);
    }
  }
}
