package org.thoughtcrime.securesms.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Listens to the event that our package was replaced, i.e. updated
 *
 * @author Lukas Barth
 */
public class PackageUpgradeReceiver extends BroadcastReceiver {

  private void handleAccountCreation(Context context) {
    Log.i("PackageUpgradeReceiver", "Ensuring TextSecure account");
    AccountUtil.ensureAccountExists(context);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    handleAccountCreation(context);
  }
}
