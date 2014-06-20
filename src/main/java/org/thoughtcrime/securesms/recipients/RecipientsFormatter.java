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

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class RecipientsFormatter {

  private static String parseBracketedNumber(String recipient) throws RecipientFormattingException {
    int begin    = recipient.indexOf('<');
    int end      = recipient.indexOf('>');
    String value = recipient.substring(begin + 1, end);

    if (PhoneNumberUtils.isWellFormedSmsAddress(value))
      return value;
    else
      throw new RecipientFormattingException("Bracketed value: " + value + " is not valid.");
  }

  private static String parseRecipient(String recipient) throws RecipientFormattingException {
    recipient = recipient.trim();

    if ((recipient.indexOf('<') != -1) && (recipient.indexOf('>') != -1))
      return parseBracketedNumber(recipient);

    if (PhoneNumberUtils.isWellFormedSmsAddress(recipient))
      return recipient;

    throw new RecipientFormattingException("Recipient: " + recipient + " is badly formatted.");
  }

  public static List<String> getRecipients(String rawText) throws RecipientFormattingException {
    ArrayList<String> results = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(rawText, ",");

    while (tokenizer.hasMoreTokens()) {
      results.add(parseRecipient(tokenizer.nextToken()));
    }

    return results;
  }

  public static String formatNameAndNumber(String name, String number) {
    // Format like this: Mike Cleron <(650) 555-1234>
    //                   Erick Tseng <(650) 555-1212>
    //                   Tutankhamun <tutank1341@gmail.com>
    //                   (408) 555-1289
    String formattedNumber = PhoneNumberUtils.formatNumber(number);
    if (!TextUtils.isEmpty(name) && !name.equals(number)) {
      return name + " <" + formattedNumber + ">";
    } else {
      return formattedNumber;
    }
  }


}
