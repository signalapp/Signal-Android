/**
 * Copyright (C) 2013 Open Whisper Systems
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
package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.MmsSendResult;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactNumberDetails;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.DirectoryUtil;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.IOException;

import ws.com.google.android.mms.pdu.SendReq;

public class UniversalTransport {

  private final Context       context;
  private final PushTransport pushTransport;
  private final SmsTransport  smsTransport;
  private final MmsTransport  mmsTransport;

  public UniversalTransport(Context context, MasterSecret masterSecret) {
    this.context       = context;
    this.pushTransport = new PushTransport(context, masterSecret);
    this.smsTransport  = new SmsTransport(context, masterSecret);
    this.mmsTransport  = new MmsTransport(context, masterSecret);
  }

  public void deliver(SmsMessageRecord message) throws UndeliverableMessageException {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      smsTransport.deliver(message);
      return;
    }

    try {
      Recipient recipient = message.getIndividualRecipient();
      String number       = Util.canonicalizeNumber(context, recipient.getNumber());

      if (isPushTransport(number)) {
        try {
          Log.w("UniversalTransport", "Delivering with GCM...");
          pushTransport.deliver(message);
        } catch (IOException ioe) {
          Log.w("UniversalTransport", ioe);
          smsTransport.deliver(message);
        }
      } else {
        Log.w("UniversalTransport", "Delivering with SMS...");
        smsTransport.deliver(message);
      }
    } catch (InvalidNumberException e) {
      Log.w("UniversalTransport", e);
      smsTransport.deliver(message);
    }
  }

  public MmsSendResult deliver(SendReq mediaMessage, long threadId)
      throws UndeliverableMessageException
  {
    if (Util.isEmpty(mediaMessage.getTo())) {
      throw new UndeliverableMessageException("No destination specified");
    }

    if (!TextSecurePreferences.isPushRegistered(context)) {
      return mmsTransport.deliver(mediaMessage);
    }

    if (isMultipleRecipients(mediaMessage)) {
      return mmsTransport.deliver(mediaMessage);
    }

    if (isPushTransport(mediaMessage.getTo()[0].getString())) {
      try {
        Log.w("UniversalTransport", "Delivering media message with GCM...");
        pushTransport.deliver(mediaMessage, threadId);
        return new MmsSendResult("push".getBytes("UTF-8"), 0, true);
      } catch (IOException ioe) {
        Log.w("UniversalTransport", ioe);
        return mmsTransport.deliver(mediaMessage);
      }
    } else {
      Log.w("UniversalTransport", "Delivering media message with MMS...");
      return mmsTransport.deliver(mediaMessage);
    }
  }

  public boolean isMultipleRecipients(SendReq mediaMessage) {
    int recipientCount = 0;

    if (mediaMessage.getTo() != null) {
      recipientCount += mediaMessage.getTo().length;
    }

    if (mediaMessage.getCc() != null) {
      recipientCount += mediaMessage.getCc().length;
    }

    if (mediaMessage.getBcc() != null) {
      recipientCount += mediaMessage.getBcc().length;
    }

    return recipientCount > 1;
  }

  private boolean isPushTransport(String destination) {
    if (GroupUtil.isEncodedGroup(destination)) {
      return true;
    }

    Directory directory = Directory.getInstance(context);

    try {
      return directory.isActiveNumber(destination);
    } catch (NotInDirectoryException e) {
      try {
        PushServiceSocket    socket          = PushServiceSocketFactory.create(context);
        ContactTokenDetails  registeredUser  = socket.getContactTokenDetails(DirectoryUtil.getDirectoryServerToken(destination));
        boolean              registeredFound = !(registeredUser == null);
        ContactNumberDetails numberDetails   = new ContactNumberDetails(destination, registeredUser == null ? null : registeredUser.getRelay());

        directory.setNumber(numberDetails, registeredFound);
        return registeredFound;
      } catch (IOException e1) {
        Log.w("UniversalTransport", e1);
        return false;
      }
    }
  }
}
