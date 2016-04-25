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
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.CanonicalAddressDatabase;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class RecipientFactory {

  private static final RecipientProvider provider = new RecipientProvider();

  public static Recipients getRecipientsForIds(Context context, String recipientIds, boolean asynchronous) {
    if (TextUtils.isEmpty(recipientIds))
      return new Recipients(new LinkedList<Recipient>());

    List<Recipient> results   = new LinkedList<>();
    StringTokenizer tokenizer = new StringTokenizer(recipientIds.trim(), " ");

    while (tokenizer.hasMoreTokens()) {
      String recipientId  = tokenizer.nextToken();
      Recipient recipient = getRecipientFromProviderId(context, Long.parseLong(recipientId), asynchronous);

      results.add(recipient);
    }

    return new Recipients(results);
  }

  public static Recipient getRecipientForId(Context context, long recipientId, boolean asynchronous) {
    return getRecipientFromProviderId(context, recipientId, asynchronous);
  }

  public static Recipients getRecipientsForIds(Context context, long[] recipientIds, boolean asynchronous) {
    List<Recipient> results = new LinkedList<>();
    if (recipientIds == null) return new Recipients(results);
    for (long recipientId : recipientIds) {
      results.add(getRecipientFromProviderId(context, recipientId, asynchronous));
    }
    return new Recipients(results);
  }

  private static Recipient getRecipientForNumber(Context context, String number, boolean asynchronous) {
    long recipientId = CanonicalAddressDatabase.getInstance(context).getCanonicalAddressId(number);
    return provider.getRecipient(context, recipientId, asynchronous);
  }

  public static Recipients getRecipientsFromString(Context context, String rawText, boolean asynchronous)
  {
    if(rawText == null) Recipient.getUnknownRecipient(context);

    List<Recipient> results   = new LinkedList<Recipient>();
    StringTokenizer tokenizer = new StringTokenizer(rawText, ",");

    while (tokenizer.hasMoreTokens()) {
      Recipient recipient = parseRecipient(context, tokenizer.nextToken(), asynchronous);
      if( recipient != null )
        results.add(recipient);
    }

    return new Recipients(results);
  }

  private static Recipient getRecipientFromProviderId(Context context, long recipientId, boolean asynchronous) {
    try {
      return provider.getRecipient(context, recipientId, asynchronous);
    } catch (NumberFormatException e) {
      Log.w("RecipientFactory", e);
      return Recipient.getUnknownRecipient(context);
    }
  }

  private static boolean hasBracketedNumber(String recipient) {
    int openBracketIndex = recipient.indexOf('<');

    return (openBracketIndex != -1) &&
           (recipient.indexOf('>', openBracketIndex) != -1);
  }

  private static String parseBracketedNumber(String recipient) {
    int begin    = recipient.indexOf('<');
    int end      = recipient.indexOf('>', begin);
    String value = recipient.substring(begin + 1, end);

    return value;
  }

  private static Recipient parseRecipient(Context context, String recipient, boolean asynchronous) {
    recipient = recipient.trim();

    if( recipient.length() == 0 )
      return null;

    if (hasBracketedNumber(recipient))
      return getRecipientForNumber(context, parseBracketedNumber(recipient), asynchronous);

    return getRecipientForNumber(context, recipient, asynchronous);
  }

  public static void clearCache() {
    ContactPhotoFactory.clearCache();
    provider.clearCache();
  }

  public static void clearCache(Recipient recipient) {
    ContactPhotoFactory.clearCache(recipient);
    provider.clearCache(recipient);
  }

}
