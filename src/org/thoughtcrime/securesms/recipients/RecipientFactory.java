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
import android.util.Log;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.util.Util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class RecipientFactory {

  private static final RecipientProvider provider = new RecipientProvider();

  public static Recipients getRecipientsForIds(Context context, String recipientIds, boolean asynchronous) {
    if (recipientIds == null || recipientIds.trim().length() == 0)
      return new Recipients(new LinkedList<Recipient>());

    List<Recipient> results   = new LinkedList<Recipient>();
    StringTokenizer tokenizer = new StringTokenizer(recipientIds.trim(), " ");

    while (tokenizer.hasMoreTokens()) {
      String recipientId  = tokenizer.nextToken();
      Recipient recipient = getRecipientFromProviderId(context, recipientId, asynchronous);

      results.add(recipient);
    }

    return new Recipients(results);
  }

  private static Recipient getRecipientForNumber(Context context, String number, boolean asynchronous) {
    return provider.getRecipient(context, number, asynchronous);
  }

  public static Recipients getRecipientsFromString(Context context, String rawText, boolean asynchronous)
      throws RecipientFormattingException
  {
    if (rawText == null) {
      throw new RecipientFormattingException("Null recipient string specified");
    }

    List<Recipient> results   = new LinkedList<Recipient>();
    StringTokenizer tokenizer = new StringTokenizer(rawText, ",");

    while (tokenizer.hasMoreTokens()) {
      Recipient recipient = parseRecipient(context, tokenizer.nextToken(), asynchronous);
      if( recipient != null )
        results.add(recipient);
    }

    return new Recipients(results);
  }

  public static Recipients getRecipientsFromMessage(Context context,
                                                    IncomingPushMessage message,
                                                    boolean asynchronous)
  {
    Set<String> recipients = new HashSet<String>();
    recipients.add(message.getSource());
    recipients.addAll(message.getDestinations());

    try {
      return getRecipientsFromString(context, Util.join(recipients, ","), asynchronous);
    } catch (RecipientFormattingException e) {
      Log.w("RecipientFactory", e);
      return new Recipients(new Recipient("Unknown", "Unknown", null,
                                          ContactPhotoFactory.getDefaultContactPhoto(context)));
    }
  }

  private static Recipient getRecipientFromProviderId(Context context, String recipientId, boolean asynchronous) {
    String number = DatabaseFactory.getAddressDatabase(context).getAddressFromId(recipientId);
    return getRecipientForNumber(context, number, asynchronous);
  }

  private static boolean hasBracketedNumber(String recipient) {
    int openBracketIndex = recipient.indexOf('<');

    return (openBracketIndex != -1) &&
           (recipient.indexOf('>', openBracketIndex) != -1);
  }

  private static String parseBracketedNumber(String recipient)
      throws RecipientFormattingException
  {
    int begin    = recipient.indexOf('<');
    int end      = recipient.indexOf('>', begin);
    String value = recipient.substring(begin + 1, end);

    if (NumberUtil.isValidSmsOrEmail(value))
      return value;
    else
      throw new RecipientFormattingException("Bracketed value: " + value + " is not valid.");
  }

  private static Recipient parseRecipient(Context context, String recipient, boolean asynchronous)
      throws RecipientFormattingException
  {
    recipient = recipient.trim();

    if( recipient.length() == 0 )
      return null;

    if (hasBracketedNumber(recipient))
      return getRecipientForNumber(context, parseBracketedNumber(recipient), asynchronous);

    if (NumberUtil.isValidSmsOrEmail(recipient))
      return getRecipientForNumber(context, recipient, asynchronous);

    throw new RecipientFormattingException("Recipient: " + recipient + " is badly formatted.");
  }

  public static void clearCache() {
    ContactPhotoFactory.clearCache();
    provider.clearCache();
  }

}
