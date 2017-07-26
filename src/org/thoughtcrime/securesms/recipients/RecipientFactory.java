/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.database.Address;

import java.util.Collection;

public class RecipientFactory {

  public static final String RECIPIENT_CLEAR_ACTION = "org.thoughtcrime.securesms.database.RecipientFactory.CLEAR";

  private static final RecipientProvider provider = new RecipientProvider();

  public static @NonNull Recipients getRecipientsFor(Context context, Collection<Recipient> recipients, boolean asynchronous) {
    Address[] addresses= new Address[recipients.size()];
    int    i   = 0;

    for (Recipient recipient : recipients) {
      addresses[i++] = recipient.getAddress();
    }

    return provider.getRecipients(context, addresses, asynchronous);
  }

  public static Recipients getRecipientsFor(Context context, Recipient recipient, boolean asynchronous) {
    Address[] addresses = new Address[1];
    addresses[0] = recipient.getAddress();

    return provider.getRecipients(context, addresses, asynchronous);
  }

  public static @NonNull Recipients getRecipientsFor(@NonNull Context context, @NonNull Address[] addresses, boolean asynchronous) {
    if (addresses == null || addresses.length == 0) throw new AssertionError(addresses);
    return provider.getRecipients(context, addresses, asynchronous);
  }

  public static @NonNull Recipient getRecipientFor(@NonNull Context context, @NonNull Address address, boolean asynchronous) {
    if (address == null) throw new AssertionError(address);
    return provider.getRecipient(context, address, asynchronous);
  }

  public static void clearCache(Context context) {
    provider.clearCache();
    context.sendBroadcast(new Intent(RECIPIENT_CLEAR_ACTION));
  }

}
