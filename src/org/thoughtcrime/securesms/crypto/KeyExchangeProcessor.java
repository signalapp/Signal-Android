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
package org.thoughtcrime.securesms.crypto;

import java.util.LinkedList;
import java.util.List;

import org.bouncycastle.util.Arrays;
import org.thoughtcrime.securesms.database.LocalKeyRecord;
import org.thoughtcrime.securesms.database.RemoteKeyRecord;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.protocol.Message;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Conversions;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This class processes key exchange interactions.
 * 
 * @author Moxie Marlinspike
 */

public class KeyExchangeProcessor {
	
  public static final String SECURITY_UPDATE_EVENT = "org.thoughtcrime.securesms.KEY_EXCHANGE_UPDATE";

  private Context context;
  private Recipient recipient;
  private MasterSecret masterSecret;
  private LocalKeyRecord localKeyRecord;
  private RemoteKeyRecord remoteKeyRecord;
  private SessionRecord sessionRecord;

  public KeyExchangeProcessor(Context context, MasterSecret masterSecret, Recipient recipient) {
    this.context         = context;
    this.recipient       = recipient;
    this.masterSecret    = masterSecret;
		
    this.remoteKeyRecord = new RemoteKeyRecord(context, recipient);
    this.localKeyRecord  = new LocalKeyRecord(context, masterSecret, recipient);
    this.sessionRecord   = new SessionRecord(context, masterSecret, recipient);
  }

  public boolean hasCompletedSession() {
    return sessionRecord.getLocalFingerprint() != null;
  }
	
  public boolean hasSameSessionIdentity(KeyExchangeMessage message) {
    return 
      (this.sessionRecord.getIdentityKey() != null) &&
      (message.getIdentityKey() != null)            &&
      (this.sessionRecord.getIdentityKey().equals(message.getIdentityKey()) &&
       !isRemoteKeyExchangeForExistingSession(message));
  }
	
  public boolean hasInitiatedSession() {
    return localKeyRecord.getCurrentKeyPair() != null;
  }
	
  private boolean needsResponseFromUs() {
    return !hasInitiatedSession() || remoteKeyRecord.getCurrentRemoteKey() != null;
  }

  public boolean isRemoteKeyExchangeForExistingSession(KeyExchangeMessage message) {
    return Arrays.areEqual(message.getPublicKey().getFingerprintBytes(), sessionRecord.getRemoteFingerprint());
  }
	
  public boolean isLocalKeyExchangeForExistingSession(KeyExchangeMessage message) {
    return Arrays.areEqual(message.getPublicKey().getFingerprintBytes(), sessionRecord.getLocalFingerprint());
  }
		
  public boolean isStale(KeyExchangeMessage message) {
    int responseKeyId = Conversions.highBitsToMedium(message.getPublicKey().getId());
		
    Log.w("KeyExchangeProcessor", "Key Exchange High ID Bits: "  + responseKeyId);
		
    return responseKeyId != 0 &&
      (localKeyRecord.getCurrentKeyPair() != null && localKeyRecord.getCurrentKeyPair().getId() != responseKeyId);
  }
	
  public boolean processKeyExchangeMessage(KeyExchangeMessage message, long threadId) {
    int initiateKeyId = Conversions.lowBitsToMedium(message.getPublicKey().getId());		
    message.getPublicKey().setId(initiateKeyId);
						
    if (needsResponseFromUs()) {
      List<Recipient> recipients = new LinkedList<Recipient>();
      recipients.add(recipient);
		
      localKeyRecord                = KeyUtil.initializeRecordFor(recipient, context, masterSecret);
      KeyExchangeMessage ourMessage = new KeyExchangeMessage(context, masterSecret, Math.min(Message.SUPPORTED_VERSION, message.getMaxVersion()), localKeyRecord, initiateKeyId);
      Log.w("KeyExchangeProcessor", "Responding with key exchange message fingerprint: " + ourMessage.getPublicKey().getFingerprint());
      Log.w("KeyExchangeProcessor", "Which has a local key record fingerprint: " + localKeyRecord.getCurrentKeyPair().getPublicKey().getFingerprint());
      MessageSender.send(context, masterSecret, new Recipients(recipients), threadId, ourMessage.serialize(), false);
    }
		
    remoteKeyRecord.setCurrentRemoteKey(message.getPublicKey());
    remoteKeyRecord.setLastRemoteKey(message.getPublicKey());
    remoteKeyRecord.save();
		
    sessionRecord.setSessionId(localKeyRecord.getCurrentKeyPair().getPublicKey().getFingerprintBytes(),
			       remoteKeyRecord.getCurrentRemoteKey().getFingerprintBytes());
    sessionRecord.setIdentityKey(message.getIdentityKey());
    sessionRecord.setSessionVersion(Math.min(Message.SUPPORTED_VERSION, message.getMaxVersion()));
		
    Log.w("KeyExchangeUtil", "Setting session version: " + Math.min(Message.SUPPORTED_VERSION, message.getMaxVersion()));

    sessionRecord.save();	
			
    DecryptingQueue.scheduleRogueMessages(context, masterSecret, recipient);
		
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.putExtra("thread_id", threadId);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
		
    return true;
  }
	
}
