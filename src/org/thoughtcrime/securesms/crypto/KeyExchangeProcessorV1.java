package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessageV1;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.KeyPair;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.LocalKeyRecord;
import org.whispersystems.textsecure.storage.RemoteKeyRecord;
import org.whispersystems.textsecure.storage.SessionRecordV1;
import org.whispersystems.textsecure.util.Conversions;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * This class processes key exchange interactions.
 *
 * @author Moxie Marlinspike
 */

public class KeyExchangeProcessorV1 extends KeyExchangeProcessor {

  private Context         context;
  private Recipient       recipient;
  private MasterSecret    masterSecret;
  private LocalKeyRecord  localKeyRecord;
  private RemoteKeyRecord remoteKeyRecord;
  private SessionRecordV1 sessionRecord;

  public KeyExchangeProcessorV1(Context context, MasterSecret masterSecret, Recipient recipient) {
    this.context         = context;
    this.recipient       = recipient;
    this.masterSecret    = masterSecret;

    this.remoteKeyRecord = new RemoteKeyRecord(context, recipient);
    this.localKeyRecord  = new LocalKeyRecord(context, masterSecret, recipient);
    this.sessionRecord   = new SessionRecordV1(context, masterSecret, recipient);
  }

  @Override
  public boolean isTrusted(KeyExchangeMessage message) {
    return message.hasIdentityKey() && isTrusted(message.getIdentityKey());
  }

  public boolean isTrusted(IdentityKey identityKey) {
    return DatabaseFactory.getIdentityDatabase(context).isValidIdentity(masterSecret, recipient,
                                                                        identityKey);
  }

  public boolean hasInitiatedSession() {
    return localKeyRecord.getCurrentKeyPair() != null;
  }

  private boolean needsResponseFromUs() {
    return !hasInitiatedSession() || remoteKeyRecord.getCurrentRemoteKey() != null;
  }

  @Override
  public boolean isStale(KeyExchangeMessage _message) {
    KeyExchangeMessageV1 message = (KeyExchangeMessageV1)_message;
    int responseKeyId = Conversions.highBitsToMedium(message.getRemoteKey().getId());

    Log.w("KeyExchangeProcessor", "Key Exchange High ID Bits: "  + responseKeyId);

    return responseKeyId != 0 &&
        (localKeyRecord.getCurrentKeyPair() != null && localKeyRecord.getCurrentKeyPair().getId() != responseKeyId);
  }

  @Override
  public void processKeyExchangeMessage(KeyExchangeMessage _message, long threadId) {
    KeyExchangeMessageV1 message = (KeyExchangeMessageV1)_message;
    int initiateKeyId = Conversions.lowBitsToMedium(message.getRemoteKey().getId());
    message.getRemoteKey().setId(initiateKeyId);

    if (needsResponseFromUs()) {
      localKeyRecord = initializeRecordFor(context, masterSecret, recipient);

      KeyExchangeMessageV1 ourMessage = new KeyExchangeMessageV1(context, masterSecret,
                                                                 Math.min(CiphertextMessage.LEGACY_VERSION,
                                                                          message.getMaxVersion()),
                                                                 localKeyRecord, initiateKeyId);

      OutgoingKeyExchangeMessage textMessage = new OutgoingKeyExchangeMessage(recipient, ourMessage.serialize());
      Log.w("KeyExchangeProcessorV1", "Responding with key exchange message fingerprint: " + ourMessage.getRemoteKey().getFingerprint());
      Log.w("KeyExchangeProcessorV1", "Which has a local key record fingerprint: " + localKeyRecord.getCurrentKeyPair().getPublicKey().getFingerprint());
      MessageSender.send(context, masterSecret, textMessage, threadId);
    }

    remoteKeyRecord.setCurrentRemoteKey(message.getRemoteKey());
    remoteKeyRecord.setLastRemoteKey(message.getRemoteKey());
    remoteKeyRecord.save();

    sessionRecord.setSessionId(localKeyRecord.getCurrentKeyPair().getPublicKey().getFingerprintBytes(),
                               remoteKeyRecord.getCurrentRemoteKey().getFingerprintBytes());
    sessionRecord.setIdentityKey(message.getIdentityKey());
    sessionRecord.setSessionVersion(Math.min(1, message.getMaxVersion()));

    Log.w("KeyExchangeUtil", "Setting session version: " + Math.min(1, message.getMaxVersion()));

    sessionRecord.save();

    if (message.hasIdentityKey()) {
      DatabaseFactory.getIdentityDatabase(context)
                     .saveIdentity(masterSecret, recipient, message.getIdentityKey());
    }

    DecryptingQueue.scheduleRogueMessages(context, masterSecret, recipient);

    broadcastSecurityUpdateEvent(context, threadId);
  }

  private static void broadcastSecurityUpdateEvent(Context context, long threadId) {
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.putExtra("thread_id", threadId);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
  }

  public LocalKeyRecord initializeRecordFor(Context context,
                                            MasterSecret masterSecret,
                                            Recipient recipient)
  {
    Log.w("KeyExchangeProcessorV1", "Initializing local key pairs...");
    try {
      SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
      int initialId             = secureRandom.nextInt(4094) + 1;

      KeyPair        currentPair = new KeyPair(initialId, Curve.generateKeyPairForSession(1), masterSecret);
      KeyPair        nextPair    = new KeyPair(initialId + 1, Curve.generateKeyPairForSession(1), masterSecret);
      LocalKeyRecord record      = new LocalKeyRecord(context, masterSecret, recipient);

      record.setCurrentKeyPair(currentPair);
      record.setNextKeyPair(nextPair);
      record.save();

      return record;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

}
