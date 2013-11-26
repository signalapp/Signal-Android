/**
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.crypto;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessageV2;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.SessionRecordV2;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class KeyExchangeInitiator {

  public static void initiate(final Context context, final MasterSecret masterSecret, final Recipient recipient, boolean promptOnExisting) {
    if (promptOnExisting && hasInitiatedSession(context, masterSecret, recipient)) {
      AlertDialog.Builder dialog = new AlertDialog.Builder(context);
      dialog.setTitle(R.string.KeyExchangeInitiator_initiate_despite_existing_request_question);
      dialog.setMessage(R.string.KeyExchangeInitiator_youve_already_sent_a_session_initiation_request_to_this_recipient_are_you_sure);
      dialog.setIcon(android.R.drawable.ic_dialog_alert);
      dialog.setCancelable(true);
      dialog.setPositiveButton(R.string.KeyExchangeInitiator_send, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          initiateKeyExchange(context, masterSecret, recipient);
        }
      });
      dialog.setNegativeButton(android.R.string.cancel, null);
      dialog.show();
    } else {
      initiateKeyExchange(context, masterSecret, recipient);
    }
  }

  private static void initiateKeyExchange(Context context, MasterSecret masterSecret, Recipient recipient) {
    int             sequence     = getRandomSequence();
    int             flags        = KeyExchangeMessageV2.INITIATE_FLAG;
    ECKeyPair       baseKey      = Curve.generateKeyPairForSession(CiphertextMessage.CURRENT_VERSION);
    ECKeyPair       ephemeralKey = Curve.generateKeyPairForSession(CiphertextMessage.CURRENT_VERSION);
    IdentityKeyPair identityKey  = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret, Curve.DJB_TYPE);

    KeyExchangeMessageV2 message = new KeyExchangeMessageV2(sequence, flags,
                                                            baseKey.getPublicKey(),
                                                            ephemeralKey.getPublicKey(),
                                                            identityKey.getPublicKey());

    OutgoingKeyExchangeMessage textMessage = new OutgoingKeyExchangeMessage(recipient, message.serialize());

    SessionRecordV2 sessionRecordV2 = new SessionRecordV2(context, masterSecret, recipient);
    sessionRecordV2.setPendingKeyExchange(sequence, baseKey, ephemeralKey, identityKey);
    sessionRecordV2.save();

    MessageSender.send(context, masterSecret, textMessage, -1);
  }

  private static boolean hasInitiatedSession(Context context, MasterSecret masterSecret,
                                             Recipient recipient)
  {
    return
        new SessionRecordV2(context, masterSecret, recipient)
            .hasPendingKeyExchange();
  }

  private static int getRandomSequence() {
    try {
      SecureRandom random    = SecureRandom.getInstance("SHA1PRNG");
      int          candidate = Math.abs(random.nextInt());

      return candidate % 65535;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
