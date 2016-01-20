package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.util.List;

public abstract class ContactIdentityManager {

  public static ContactIdentityManager getInstance(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
      return new ContactIdentityManagerICS(context);
    else
      return new ContactIdentityManagerGingerbread(context);
  }

  protected final Context context;

  public ContactIdentityManager(Context context) {
    this.context = context.getApplicationContext();
  }

  public abstract Uri        getSelfIdentityUri();
  public abstract boolean    isSelfIdentityAutoDetected();
  public abstract List<Long> getSelfIdentityRawContactIds();

}
