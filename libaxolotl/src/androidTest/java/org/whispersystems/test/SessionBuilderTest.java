package org.whispersystems.test;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.StaleKeyExchangeException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.state.DeviceKeyRecord;
import org.whispersystems.libaxolotl.state.DeviceKeyStore;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.Pair;

import java.util.HashSet;
import java.util.Set;

public class SessionBuilderTest extends AndroidTestCase {

  private static final long ALICE_RECIPIENT_ID = 5L;
  private static final long BOB_RECIPIENT_ID   = 2L;

  public void testBasicPreKeyV2()
      throws InvalidKeyException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, UntrustedIdentityException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    ECKeyPair  bobPreKeyPair = Curve.generateKeyPair(true);
    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              0, null, null,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    assertTrue(aliceSessionStore.containsSession(BOB_RECIPIENT_ID, 1));
    assertTrue(!aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getNeedsRefresh());
    assertTrue(aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 2);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessage.serialize());
    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobSessionBuilder.process(incomingMessage);

    assertTrue(bobSessionStore.containsSession(ALICE_RECIPIENT_ID, 1));
    assertTrue(bobSessionStore.loadSession(ALICE_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 2);

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, ALICE_RECIPIENT_ID, 1);
    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage.getWhisperMessage().serialize());

    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    assertTrue(bobOutgoingMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] alicePlaintext = aliceSessionCipher.decrypt(bobOutgoingMessage.serialize());
    assertTrue(new String(alicePlaintext).equals(originalMessage));

    runInteraction(aliceSessionStore, bobSessionStore);

    aliceSessionStore     = new InMemorySessionStore();
    aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                               aliceDeviceKeyStore,
                                               aliceIdentityKeyStore,
                                               BOB_RECIPIENT_ID, 1);
    aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);

    bobPreKeyPair = Curve.generateKeyPair(true);
    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(),
                                 1, 31338, bobPreKeyPair.getPublicKey(),
                                 0, null, null, bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31338, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    aliceSessionBuilder.process(bobPreKey);

    outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    try {
      bobSessionBuilder.process(new PreKeyWhisperMessage(outgoingMessage.serialize()));
      throw new AssertionError("shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobIdentityKeyStore.saveIdentity(ALICE_RECIPIENT_ID, new PreKeyWhisperMessage(outgoingMessage.serialize()).getIdentityKey());
      bobSessionBuilder.process(new PreKeyWhisperMessage(outgoingMessage.serialize()));
    }

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()).getWhisperMessage().serialize());
    assertTrue(new String(plaintext).equals(originalMessage));

    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                 31337, Curve.generateKeyPair(true).getPublicKey(),
                                 0, null, null,
                                 aliceIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    try {
      aliceSessionBuilder.process(bobPreKey);
      throw new AssertionError("shoulnd't be trusted!");
    } catch (UntrustedIdentityException uie) {
      // good
    }
  }

  public void testBasicPreKeyV3()
      throws InvalidKeyException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, UntrustedIdentityException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    ECKeyPair bobPreKeyPair         = Curve.generateKeyPair(true);
    ECKeyPair bobDeviceKeyPair      = Curve.generateKeyPair(true);
    byte[]    bobDeviceKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                               bobDeviceKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobDeviceKeyPair.getPublicKey(), bobDeviceKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    assertTrue(aliceSessionStore.containsSession(BOB_RECIPIENT_ID, 1));
    assertTrue(!aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getNeedsRefresh());
    assertTrue(aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 3);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessage.serialize());
    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobDeviceKeyStore.storeDeviceKey(22, new DeviceKeyRecord(22, System.currentTimeMillis(), bobDeviceKeyPair, bobDeviceKeySignature));
    bobSessionBuilder.process(incomingMessage);

    assertTrue(bobSessionStore.containsSession(ALICE_RECIPIENT_ID, 1));
    assertTrue(bobSessionStore.loadSession(ALICE_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 3);
    assertTrue(bobSessionStore.loadSession(ALICE_RECIPIENT_ID, 1).getSessionState().getAliceBaseKey() != null);

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, ALICE_RECIPIENT_ID, 1);
    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage.getWhisperMessage().serialize());

    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    assertTrue(bobOutgoingMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] alicePlaintext = aliceSessionCipher.decrypt(bobOutgoingMessage.serialize());
    assertTrue(new String(alicePlaintext).equals(originalMessage));

    runInteraction(aliceSessionStore, bobSessionStore);

    aliceSessionStore     = new InMemorySessionStore();
    aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                               aliceDeviceKeyStore,
                                               aliceIdentityKeyStore,
                                               BOB_RECIPIENT_ID, 1);
    aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);

    bobPreKeyPair    = Curve.generateKeyPair(true);
    bobDeviceKeyPair = Curve.generateKeyPair(true);
    bobDeviceKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(), bobDeviceKeyPair.getPublicKey().serialize());
    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(),
                                 1, 31338, bobPreKeyPair.getPublicKey(),
                                 23, bobDeviceKeyPair.getPublicKey(), bobDeviceKeySignature,
                                 bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31338, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobDeviceKeyStore.storeDeviceKey(23, new DeviceKeyRecord(23, System.currentTimeMillis(), bobDeviceKeyPair, bobDeviceKeySignature));
    aliceSessionBuilder.process(bobPreKey);

    outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    try {
      bobSessionBuilder.process(new PreKeyWhisperMessage(outgoingMessage.serialize()));
      throw new AssertionError("shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobIdentityKeyStore.saveIdentity(ALICE_RECIPIENT_ID, new PreKeyWhisperMessage(outgoingMessage.serialize()).getIdentityKey());
      bobSessionBuilder.process(new PreKeyWhisperMessage(outgoingMessage.serialize()));
    }

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()).getWhisperMessage().serialize());
    assertTrue(new String(plaintext).equals(originalMessage));

    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                 31337, Curve.generateKeyPair(true).getPublicKey(),
                                 23, bobDeviceKeyPair.getPublicKey(), bobDeviceKeySignature,
                                 aliceIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    try {
      aliceSessionBuilder.process(bobPreKey);
      throw new AssertionError("shoulnd't be trusted!");
    } catch (UntrustedIdentityException uie) {
      // good
    }
  }

  public void testBadDeviceKeySignature() throws InvalidKeyException, UntrustedIdentityException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair         = Curve.generateKeyPair(true);
    ECKeyPair bobDeviceKeyPair      = Curve.generateKeyPair(true);
    byte[]    bobDeviceKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                               bobDeviceKeyPair.getPublicKey().serialize());


    for (int i=0;i<bobDeviceKeySignature.length * 8;i++) {
      byte[] modifiedSignature = new byte[bobDeviceKeySignature.length];
      System.arraycopy(bobDeviceKeySignature, 0, modifiedSignature, 0, modifiedSignature.length);

      modifiedSignature[i/8] ^= (0x01 << (i % 8));

      PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                                31337, bobPreKeyPair.getPublicKey(),
                                                22, bobDeviceKeyPair.getPublicKey(), modifiedSignature,
                                                bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

      try {
        aliceSessionBuilder.process(bobPreKey);
        throw new AssertionError("Accepted modified device key signature!");
      } catch (InvalidKeyException ike) {
        // good
      }
    }

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobDeviceKeyPair.getPublicKey(), bobDeviceKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);
  }

  public void testRepeatBundleMessageV2() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    ECKeyPair bobPreKeyPair         = Curve.generateKeyPair(true);
    ECKeyPair bobDeviceKeyPair      = Curve.generateKeyPair(true);
    byte[]    bobDeviceKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                               bobDeviceKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              0, null, null,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobDeviceKeyStore.storeDeviceKey(22, new DeviceKeyRecord(22, System.currentTimeMillis(), bobDeviceKeyPair, bobDeviceKeySignature));

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());
    CiphertextMessage outgoingMessageTwo = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessageOne.serialize());
    bobSessionBuilder.process(incomingMessage);

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, ALICE_RECIPIENT_ID, 1);

    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage.getWhisperMessage().serialize());
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    byte[] alicePlaintext = aliceSessionCipher.decrypt(bobOutgoingMessage.serialize());
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

    // The test

    PreKeyWhisperMessage incomingMessageTwo = new PreKeyWhisperMessage(outgoingMessageTwo.serialize());
    bobSessionBuilder.process(incomingMessageTwo);

    plaintext = bobSessionCipher.decrypt(incomingMessageTwo.getWhisperMessage().serialize());
    assertTrue(originalMessage.equals(new String(plaintext)));

    bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    alicePlaintext = aliceSessionCipher.decrypt(bobOutgoingMessage.serialize());
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

  }

  public void testRepeatBundleMessageV3() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    ECKeyPair bobPreKeyPair         = Curve.generateKeyPair(true);
    ECKeyPair bobDeviceKeyPair      = Curve.generateKeyPair(true);
    byte[]    bobDeviceKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                               bobDeviceKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobDeviceKeyPair.getPublicKey(), bobDeviceKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobDeviceKeyStore.storeDeviceKey(22, new DeviceKeyRecord(22, System.currentTimeMillis(), bobDeviceKeyPair, bobDeviceKeySignature));

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());
    CiphertextMessage outgoingMessageTwo = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessageOne.serialize());
    bobSessionBuilder.process(incomingMessage);

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, ALICE_RECIPIENT_ID, 1);

    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage.getWhisperMessage().serialize());
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    byte[] alicePlaintext = aliceSessionCipher.decrypt(bobOutgoingMessage.serialize());
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

    // The test

    PreKeyWhisperMessage incomingMessageTwo = new PreKeyWhisperMessage(outgoingMessageTwo.serialize());
    bobSessionBuilder.process(incomingMessageTwo);

    plaintext = bobSessionCipher.decrypt(incomingMessageTwo.getWhisperMessage().serialize());
    assertTrue(originalMessage.equals(new String(plaintext)));

    bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    alicePlaintext = aliceSessionCipher.decrypt(bobOutgoingMessage.serialize());
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

  }

  public void testBadVerificationTagV3() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    ECKeyPair bobPreKeyPair         = Curve.generateKeyPair(true);
    ECKeyPair bobDeviceKeyPair      = Curve.generateKeyPair(true);
    byte[]    bobDeviceKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                               bobDeviceKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobDeviceKeyPair.getPublicKey(), bobDeviceKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobDeviceKeyStore.storeDeviceKey(22, new DeviceKeyRecord(22, System.currentTimeMillis(), bobDeviceKeyPair, bobDeviceKeySignature));

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessageOne.serialize());

    for (int i=0;i<incomingMessage.getVerification().length * 8;i++) {
      byte[] modifiedVerification  = new byte[incomingMessage.getVerification().length];
      modifiedVerification[i / 8] ^= (0x01 << i % 8);

      PreKeyWhisperMessage modifiedMessage = new PreKeyWhisperMessage(incomingMessage.getMessageVersion(),
                                                                      incomingMessage.getRegistrationId(),
                                                                      incomingMessage.getPreKeyId(),
                                                                      incomingMessage.getDeviceKeyId(),
                                                                      incomingMessage.getBaseKey(),
                                                                      incomingMessage.getIdentityKey(),
                                                                      modifiedVerification,
                                                                      incomingMessage.getWhisperMessage());

      try {
        bobSessionBuilder.process(modifiedMessage);
        throw new AssertionError("Modified verification tag passed!");
      } catch (InvalidKeyException e) {
        // good
      }
    }

    PreKeyWhisperMessage unmodifiedMessage = new PreKeyWhisperMessage(incomingMessage.getMessageVersion(),
                                                                      incomingMessage.getRegistrationId(),
                                                                      incomingMessage.getPreKeyId(),
                                                                      incomingMessage.getDeviceKeyId(),
                                                                      incomingMessage.getBaseKey(),
                                                                      incomingMessage.getIdentityKey(),
                                                                      incomingMessage.getVerification(),
                                                                      incomingMessage.getWhisperMessage());

    bobSessionBuilder.process(unmodifiedMessage);
  }


  public void testBasicKeyExchange() throws InvalidKeyException, LegacyMessageException, InvalidMessageException, DuplicateMessageException, UntrustedIdentityException, StaleKeyExchangeException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    KeyExchangeMessage aliceKeyExchangeMessage = aliceSessionBuilder.process();
    KeyExchangeMessage bobKeyExchangeMessage   = bobSessionBuilder.process(aliceKeyExchangeMessage);

    assertTrue(bobKeyExchangeMessage != null);
    assertTrue(aliceKeyExchangeMessage != null);

    KeyExchangeMessage response = aliceSessionBuilder.process(bobKeyExchangeMessage);

    assertTrue(response == null);
    assertTrue(aliceSessionStore.containsSession(BOB_RECIPIENT_ID, 1));
    assertTrue(bobSessionStore.containsSession(ALICE_RECIPIENT_ID, 1));

    runInteraction(aliceSessionStore, bobSessionStore);

    aliceSessionStore       = new InMemorySessionStore();
    aliceIdentityKeyStore   = new InMemoryIdentityKeyStore();
    aliceSessionBuilder     = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                 aliceDeviceKeyStore,
                                                 aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    aliceKeyExchangeMessage = aliceSessionBuilder.process();

    try {
      bobKeyExchangeMessage = bobSessionBuilder.process(aliceKeyExchangeMessage);
      throw new AssertionError("This identity shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobIdentityKeyStore.saveIdentity(ALICE_RECIPIENT_ID, aliceKeyExchangeMessage.getIdentityKey());
      bobKeyExchangeMessage = bobSessionBuilder.process(aliceKeyExchangeMessage);
    }

    assertTrue(aliceSessionBuilder.process(bobKeyExchangeMessage) == null);

    runInteraction(aliceSessionStore, bobSessionStore);
  }

  public void testSimultaneousKeyExchange()
      throws InvalidKeyException, DuplicateMessageException, LegacyMessageException, InvalidMessageException, UntrustedIdentityException, StaleKeyExchangeException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   aliceDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceDeviceKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    DeviceKeyStore   bobDeviceKeyStore   = new InMemoryDeviceKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobDeviceKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    KeyExchangeMessage aliceKeyExchange = aliceSessionBuilder.process();
    KeyExchangeMessage bobKeyExchange   = bobSessionBuilder.process();

    assertTrue(aliceKeyExchange != null);
    assertTrue(bobKeyExchange != null);

    KeyExchangeMessage aliceResponse = aliceSessionBuilder.process(bobKeyExchange);
    KeyExchangeMessage bobResponse   = bobSessionBuilder.process(aliceKeyExchange);

    assertTrue(aliceResponse != null);
    assertTrue(bobResponse != null);

    KeyExchangeMessage aliceAck = aliceSessionBuilder.process(bobResponse);
    KeyExchangeMessage bobAck   = bobSessionBuilder.process(aliceResponse);

    assertTrue(aliceAck == null);
    assertTrue(bobAck == null);

    runInteraction(aliceSessionStore, bobSessionStore);
  }

  private void runInteraction(SessionStore aliceSessionStore, SessionStore bobSessionStore)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException
  {
    SessionCipher aliceSessionCipher = new SessionCipher(aliceSessionStore, BOB_RECIPIENT_ID, 1);
    SessionCipher bobSessionCipher   = new SessionCipher(bobSessionStore, ALICE_RECIPIENT_ID, 1);

    String originalMessage = "smert ze smert";
    CiphertextMessage aliceMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(aliceMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] plaintext = bobSessionCipher.decrypt(aliceMessage.serialize());
    assertTrue(new String(plaintext).equals(originalMessage));

    CiphertextMessage bobMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(bobMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    plaintext = aliceSessionCipher.decrypt(bobMessage.serialize());
    assertTrue(new String(plaintext).equals(originalMessage));

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = bobSessionCipher.decrypt(aliceLoopingMessage.serialize());
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = aliceSessionCipher.decrypt(bobLoopingMessage.serialize());
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    Set<Pair<String, CiphertextMessage>> aliceOutOfOrderMessages = new HashSet<>();

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      aliceOutOfOrderMessages.add(new Pair<>(loopingMessage, aliceLoopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = bobSessionCipher.decrypt(aliceLoopingMessage.serialize());
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("You can only desire based on what you know: " + i);
      CiphertextMessage bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = aliceSessionCipher.decrypt(bobLoopingMessage.serialize());
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (Pair<String, CiphertextMessage> aliceOutOfOrderMessage : aliceOutOfOrderMessages) {
      byte[] outOfOrderPlaintext = bobSessionCipher.decrypt(aliceOutOfOrderMessage.second().serialize());
      assertTrue(new String(outOfOrderPlaintext).equals(aliceOutOfOrderMessage.first()));
    }
  }


}
