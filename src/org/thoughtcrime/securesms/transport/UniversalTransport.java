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
import android.util.Pair;

import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.pdu.EncodedStringValue;
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
  }

  public Pair<byte[], Integer> deliver(SendReq mediaMessage) throws UndeliverableMessageException {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return mmsTransport.deliver(mediaMessage);
    }

    List<String> destinations = getMediaDestinations(mediaMessage);

    if (isPushTransport(destinations)) {
      try {
        Log.w("UniversalTransport", "Delivering media message with GCM...");
        pushTransport.deliver(mediaMessage, destinations);
        return new Pair<byte[], Integer>("push".getBytes("UTF-8"), 0);
      } catch (IOException ioe) {
        Log.w("UniversalTransport", ioe);
        return mmsTransport.deliver(mediaMessage);
      }
    } else {
      Log.w("UniversalTransport", "Delivering media message with MMS...");
      return mmsTransport.deliver(mediaMessage);
    }
  }

  private List<String> getMediaDestinations(SendReq mediaMessage) {
    LinkedList<String> destinations = new LinkedList<String>();

    if (mediaMessage.getTo() != null) {
      for (EncodedStringValue to : mediaMessage.getTo()) {
        destinations.add(Util.canonicalizeNumber(context, to.getString()));
      }
    }

    if (mediaMessage.getCc() != null) {
      for (EncodedStringValue cc : mediaMessage.getCc()) {
        destinations.add(Util.canonicalizeNumber(context, cc.getString()));
      }
    }

    if (mediaMessage.getBcc() != null) {
      for (EncodedStringValue bcc : mediaMessage.getBcc()) {
        destinations.add(Util.canonicalizeNumber(context, bcc.getString()));
      }
    }

    return destinations;
  }

  private boolean isPushTransport(String destination) {
    Directory directory = Directory.getInstance(context);

    try {
      return directory.isActiveNumber(destination);
    } catch (NotInDirectoryException e) {
      try {
        String              localNumber    = TextSecurePreferences.getLocalNumber(context);
        String              pushPassword   = TextSecurePreferences.getPushServerPassword(context);
        String              contactToken   = directory.getToken(destination);
        PushServiceSocket   socket         = new PushServiceSocket(context, localNumber, pushPassword);
        ContactTokenDetails registeredUser = socket.getContactTokenDetails(contactToken);

        if (registeredUser == null) {
          registeredUser = new ContactTokenDetails(contactToken);
          directory.setToken(registeredUser, false);
          return false;
        } else {
          directory.setToken(registeredUser, true);
          return true;
        }
      } catch (IOException e1) {
        Log.w("UniversalTransport", e1);
        return false;
      }
    }
  }

  private boolean isPushTransport(List<String> destinations) {
    for (String destination : destinations) {
      if (!isPushTransport(destination)) {
        return false;
      }
    }

    return true;
  }
}
