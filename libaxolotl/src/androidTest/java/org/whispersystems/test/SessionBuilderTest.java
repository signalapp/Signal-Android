package org.whispersystems.test;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.StaleKeyExchangeException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.libaxolotl.util.Pair;

import java.util.HashSet;
import java.util.Set;

public class SessionBuilderTest extends AndroidTestCase {

  private static final long ALICE_RECIPIENT_ID = 5L;
  private static final long BOB_RECIPIENT_ID   = 2L;

  public void testBasicPreKeyV2()
      throws InvalidKeyException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, UntrustedIdentityException, NoSessionException {
    SessionStore      aliceSessionStore      = new InMemorySessionStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    PreKeyStore       alicePreKeyStore       = new InMemoryPreKeyStore();
    IdentityKeyStore  aliceIdentityKeyStore  = new InMemoryIdentityKeyStore();
    SessionBuilder    aliceSessionBuilder    = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                  aliceSignedPreKeyStore,
                                                                  aliceIdentityKeyStore,
                                                                  BOB_RECIPIENT_ID, 1);

    SessionStore      bobSessionStore      = new InMemorySessionStore();
    PreKeyStore       bobPreKeyStore       = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore  bobIdentityKeyStore  = new InMemoryIdentityKeyStore();

    ECKeyPair    bobPreKeyPair = Curve.generateKeyPair();
    PreKeyBundle bobPreKey     = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                                  31337, bobPreKeyPair.getPublicKey(),
                                                  0, null, null,
                                                  bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    assertTrue(aliceSessionStore.containsSession(BOB_RECIPIENT_ID, 1));
    assertTrue(!aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getNeedsRefresh());
    assertTrue(aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 2);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessage.serialize());
    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, ALICE_RECIPIENT_ID, 1);
    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage);

    assertTrue(bobSessionStore.containsSession(ALICE_RECIPIENT_ID, 1));
    assertTrue(bobSessionStore.loadSession(ALICE_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 2);
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    assertTrue(bobOutgoingMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] alicePlaintext = aliceSessionCipher.decrypt((WhisperMessage)bobOutgoingMessage);
    assertTrue(new String(alicePlaintext).equals(originalMessage));

    runInteraction(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore,
                   bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore);

    aliceSessionStore     = new InMemorySessionStore();
    aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                               aliceSignedPreKeyStore,
                                               aliceIdentityKeyStore,
                                               BOB_RECIPIENT_ID, 1);
    aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);

    bobPreKeyPair = Curve.generateKeyPair();
    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(),
                                 1, 31338, bobPreKeyPair.getPublicKey(),
                                 0, null, null, bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31338, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    aliceSessionBuilder.process(bobPreKey);

    outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    try {
      bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()));
      throw new AssertionError("shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobIdentityKeyStore.saveIdentity(ALICE_RECIPIENT_ID, new PreKeyWhisperMessage(outgoingMessage.serialize()).getIdentityKey());
    }

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()));

    assertTrue(new String(plaintext).equals(originalMessage));

    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                 31337, Curve.generateKeyPair().getPublicKey(),
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
      throws InvalidKeyException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, UntrustedIdentityException, NoSessionException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceSignedPreKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair            = Curve.generateKeyPair();
    ECKeyPair bobSignedPreKeyPair      = Curve.generateKeyPair();
    byte[]    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                                  bobSignedPreKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobSignedPreKeyPair.getPublicKey(),
                                              bobSignedPreKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);

    assertTrue(aliceSessionStore.containsSession(BOB_RECIPIENT_ID, 1));
    assertTrue(!aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getNeedsRefresh());
    assertTrue(aliceSessionStore.loadSession(BOB_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 3);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessage    = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessage.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessage.serialize());
    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobSignedPreKeyStore.storeSignedPreKey(22, new SignedPreKeyRecord(22, System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, ALICE_RECIPIENT_ID, 1);
    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage);


    assertTrue(bobSessionStore.containsSession(ALICE_RECIPIENT_ID, 1));
    assertTrue(bobSessionStore.loadSession(ALICE_RECIPIENT_ID, 1).getSessionState().getSessionVersion() == 3);
    assertTrue(bobSessionStore.loadSession(ALICE_RECIPIENT_ID, 1).getSessionState().getAliceBaseKey() != null);
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    assertTrue(bobOutgoingMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(new String(alicePlaintext).equals(originalMessage));

    runInteraction(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore,
                   bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore);

    aliceSessionStore     = new InMemorySessionStore();
    aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                               aliceSignedPreKeyStore,
                                               aliceIdentityKeyStore,
                                               BOB_RECIPIENT_ID, 1);
    aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);

    bobPreKeyPair            = Curve.generateKeyPair();
    bobSignedPreKeyPair      = Curve.generateKeyPair();
    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(), bobSignedPreKeyPair.getPublicKey().serialize());
    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(),
                                 1, 31338, bobPreKeyPair.getPublicKey(),
                                 23, bobSignedPreKeyPair.getPublicKey(), bobSignedPreKeySignature,
                                 bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31338, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobSignedPreKeyStore.storeSignedPreKey(23, new SignedPreKeyRecord(23, System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));
    aliceSessionBuilder.process(bobPreKey);

    outgoingMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    try {
      plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()));
      throw new AssertionError("shouldn't be trusted!");
    } catch (UntrustedIdentityException uie) {
      bobIdentityKeyStore.saveIdentity(ALICE_RECIPIENT_ID, new PreKeyWhisperMessage(outgoingMessage.serialize()).getIdentityKey());
    }

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(outgoingMessage.serialize()));
    assertTrue(new String(plaintext).equals(originalMessage));

    bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                 31337, Curve.generateKeyPair().getPublicKey(),
                                 23, bobSignedPreKeyPair.getPublicKey(), bobSignedPreKeySignature,
                                 aliceIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    try {
      aliceSessionBuilder.process(bobPreKey);
      throw new AssertionError("shoulnd't be trusted!");
    } catch (UntrustedIdentityException uie) {
      // good
    }
  }

  public void testBadSignedPreKeySignature() throws InvalidKeyException, UntrustedIdentityException {
    SessionStore      aliceSessionStore      = new InMemorySessionStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    PreKeyStore       alicePreKeyStore       = new InMemoryPreKeyStore();
    IdentityKeyStore  aliceIdentityKeyStore  = new InMemoryIdentityKeyStore();
    SessionBuilder    aliceSessionBuilder    = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                  aliceSignedPreKeyStore,
                                                                  aliceIdentityKeyStore,
                                                                  BOB_RECIPIENT_ID, 1);

    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair            = Curve.generateKeyPair();
    ECKeyPair bobSignedPreKeyPair      = Curve.generateKeyPair();
    byte[]    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                                  bobSignedPreKeyPair.getPublicKey().serialize());


    for (int i=0;i<bobSignedPreKeySignature.length * 8;i++) {
      byte[] modifiedSignature = new byte[bobSignedPreKeySignature.length];
      System.arraycopy(bobSignedPreKeySignature, 0, modifiedSignature, 0, modifiedSignature.length);

      modifiedSignature[i/8] ^= (0x01 << (i % 8));

      PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                                31337, bobPreKeyPair.getPublicKey(),
                                                22, bobSignedPreKeyPair.getPublicKey(), modifiedSignature,
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
                                              22, bobSignedPreKeyPair.getPublicKey(), bobSignedPreKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    aliceSessionBuilder.process(bobPreKey);
  }

  public void testRepeatBundleMessageV2() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, NoSessionException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceSignedPreKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair            = Curve.generateKeyPair();
    ECKeyPair bobSignedPreKeyPair      = Curve.generateKeyPair();
    byte[]    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                                  bobSignedPreKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              0, null, null,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobSignedPreKeyStore.storeSignedPreKey(22, new SignedPreKeyRecord(22, System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());
    CiphertextMessage outgoingMessageTwo = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessageOne.serialize());

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, ALICE_RECIPIENT_ID, 1);

    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage);
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

    // The test

    PreKeyWhisperMessage incomingMessageTwo = new PreKeyWhisperMessage(outgoingMessageTwo.serialize());

    plaintext = bobSessionCipher.decrypt(incomingMessageTwo);
    assertTrue(originalMessage.equals(new String(plaintext)));

    bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

  }

  public void testRepeatBundleMessageV3() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, InvalidKeyIdException, DuplicateMessageException, LegacyMessageException, NoSessionException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceSignedPreKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair            = Curve.generateKeyPair();
    ECKeyPair bobSignedPreKeyPair      = Curve.generateKeyPair();
    byte[]    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                                  bobSignedPreKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobSignedPreKeyPair.getPublicKey(), bobSignedPreKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobSignedPreKeyStore.storeSignedPreKey(22, new SignedPreKeyRecord(22, System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());
    CiphertextMessage outgoingMessageTwo = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    PreKeyWhisperMessage incomingMessage = new PreKeyWhisperMessage(outgoingMessageOne.serialize());

    SessionCipher bobSessionCipher = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, ALICE_RECIPIENT_ID, 1);

    byte[]        plaintext        = bobSessionCipher.decrypt(incomingMessage);
    assertTrue(originalMessage.equals(new String(plaintext)));

    CiphertextMessage bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

    // The test

    PreKeyWhisperMessage incomingMessageTwo = new PreKeyWhisperMessage(outgoingMessageTwo.serialize());

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(incomingMessageTwo.serialize()));
    assertTrue(originalMessage.equals(new String(plaintext)));

    bobOutgoingMessage = bobSessionCipher.encrypt(originalMessage.getBytes());
    alicePlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobOutgoingMessage.serialize()));
    assertTrue(originalMessage.equals(new String(alicePlaintext)));

  }

  public void testBadMessageBundle() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, LegacyMessageException, InvalidKeyIdException {
    SessionStore      aliceSessionStore      = new InMemorySessionStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    PreKeyStore       alicePreKeyStore       = new InMemoryPreKeyStore();
    IdentityKeyStore  aliceIdentityKeyStore  = new InMemoryIdentityKeyStore();
    SessionBuilder    aliceSessionBuilder    = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                  aliceSignedPreKeyStore,
                                                                  aliceIdentityKeyStore,
                                                                  BOB_RECIPIENT_ID, 1);

    SessionStore      bobSessionStore      = new InMemorySessionStore();
    PreKeyStore       bobPreKeyStore       = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore  bobIdentityKeyStore  = new InMemoryIdentityKeyStore();

    ECKeyPair bobPreKeyPair            = Curve.generateKeyPair();
    ECKeyPair bobSignedPreKeyPair      = Curve.generateKeyPair();
    byte[]    bobSignedPreKeySignature = Curve.calculateSignature(bobIdentityKeyStore.getIdentityKeyPair().getPrivateKey(),
                                                                  bobSignedPreKeyPair.getPublicKey().serialize());

    PreKeyBundle bobPreKey = new PreKeyBundle(bobIdentityKeyStore.getLocalRegistrationId(), 1,
                                              31337, bobPreKeyPair.getPublicKey(),
                                              22, bobSignedPreKeyPair.getPublicKey(), bobSignedPreKeySignature,
                                              bobIdentityKeyStore.getIdentityKeyPair().getPublicKey());

    bobPreKeyStore.storePreKey(31337, new PreKeyRecord(bobPreKey.getPreKeyId(), bobPreKeyPair));
    bobSignedPreKeyStore.storeSignedPreKey(22, new SignedPreKeyRecord(22, System.currentTimeMillis(), bobSignedPreKeyPair, bobSignedPreKeySignature));

    aliceSessionBuilder.process(bobPreKey);

    String            originalMessage    = "L'homme est condamné à être libre";
    SessionCipher     aliceSessionCipher = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    CiphertextMessage outgoingMessageOne = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(outgoingMessageOne.getType() == CiphertextMessage.PREKEY_TYPE);

    byte[] goodMessage = outgoingMessageOne.serialize();
    byte[] badMessage  = new byte[goodMessage.length];
    System.arraycopy(goodMessage, 0, badMessage, 0, badMessage.length);

    badMessage[badMessage.length-10] ^= 0x01;

    PreKeyWhisperMessage incomingMessage  = new PreKeyWhisperMessage(badMessage);
    SessionCipher        bobSessionCipher = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, ALICE_RECIPIENT_ID, 1);

    byte[] plaintext = new byte[0];

    try {
      plaintext = bobSessionCipher.decrypt(incomingMessage);
      throw new AssertionError("Decrypt should have failed!");
    } catch (InvalidMessageException e) {
      // good.
    }

    assertTrue(bobPreKeyStore.containsPreKey(31337));

    plaintext = bobSessionCipher.decrypt(new PreKeyWhisperMessage(goodMessage));

    assertTrue(originalMessage.equals(new String(plaintext)));
    assertTrue(!bobPreKeyStore.containsPreKey(31337));
  }

  public void testBasicKeyExchange() throws InvalidKeyException, LegacyMessageException, InvalidMessageException, DuplicateMessageException, UntrustedIdentityException, StaleKeyExchangeException, InvalidVersionException, NoSessionException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceSignedPreKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobSignedPreKeyStore,
                                                              bobIdentityKeyStore,
                                                              ALICE_RECIPIENT_ID, 1);

    KeyExchangeMessage aliceKeyExchangeMessage      = aliceSessionBuilder.process();
    assertTrue(aliceKeyExchangeMessage != null);

    byte[]             aliceKeyExchangeMessageBytes = aliceKeyExchangeMessage.serialize();
    KeyExchangeMessage bobKeyExchangeMessage        = bobSessionBuilder.process(new KeyExchangeMessage(aliceKeyExchangeMessageBytes));

    assertTrue(bobKeyExchangeMessage != null);

    byte[]             bobKeyExchangeMessageBytes = bobKeyExchangeMessage.serialize();
    KeyExchangeMessage response                   = aliceSessionBuilder.process(new KeyExchangeMessage(bobKeyExchangeMessageBytes));

    assertTrue(response == null);
    assertTrue(aliceSessionStore.containsSession(BOB_RECIPIENT_ID, 1));
    assertTrue(bobSessionStore.containsSession(ALICE_RECIPIENT_ID, 1));

    runInteraction(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore,
                   bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore);

    aliceSessionStore       = new InMemorySessionStore();
    aliceIdentityKeyStore   = new InMemoryIdentityKeyStore();
    aliceSessionBuilder     = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                 aliceSignedPreKeyStore,
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

    runInteraction(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore,
                   bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore);
  }

  public void testSimultaneousKeyExchange()
      throws InvalidKeyException, DuplicateMessageException, LegacyMessageException, InvalidMessageException, UntrustedIdentityException, StaleKeyExchangeException, NoSessionException {
    SessionStore     aliceSessionStore     = new InMemorySessionStore();
    PreKeyStore      alicePreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore aliceIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   aliceSessionBuilder   = new SessionBuilder(aliceSessionStore, alicePreKeyStore,
                                                                aliceSignedPreKeyStore,
                                                                aliceIdentityKeyStore,
                                                                BOB_RECIPIENT_ID, 1);

    SessionStore     bobSessionStore     = new InMemorySessionStore();
    PreKeyStore      bobPreKeyStore      = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore bobIdentityKeyStore = new InMemoryIdentityKeyStore();
    SessionBuilder   bobSessionBuilder   = new SessionBuilder(bobSessionStore, bobPreKeyStore,
                                                              bobSignedPreKeyStore,
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

    runInteraction(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore,
                   bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore);
  }

  private void runInteraction(SessionStore aliceSessionStore,
                              PreKeyStore alicePreKeyStore,
                              SignedPreKeyStore aliceSignedPreKeyStore,
                              IdentityKeyStore aliceIdentityKeyStore,
                              SessionStore bobSessionStore,
                              PreKeyStore bobPreKeyStore,
                              SignedPreKeyStore bobSignedPreKeyStore,
                              IdentityKeyStore bobIdentityKeyStore)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, NoSessionException
  {
    SessionCipher aliceSessionCipher = new SessionCipher(aliceSessionStore,  alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, BOB_RECIPIENT_ID, 1);
    SessionCipher bobSessionCipher   = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, ALICE_RECIPIENT_ID, 1);

    String originalMessage = "smert ze smert";
    CiphertextMessage aliceMessage = aliceSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(aliceMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] plaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceMessage.serialize()));
    assertTrue(new String(plaintext).equals(originalMessage));

    CiphertextMessage bobMessage = bobSessionCipher.encrypt(originalMessage.getBytes());

    assertTrue(bobMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    plaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobMessage.serialize()));
    assertTrue(new String(plaintext).equals(originalMessage));

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage aliceLoopingMessage = aliceSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("What do we mean by saying that existence precedes essence? " +
                               "We mean that man first of all exists, encounters himself, " +
                               "surges up in the world--and defines himself aftward. " + i);
      CiphertextMessage bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobLoopingMessage.serialize()));
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

      byte[] loopingPlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (int i=0;i<10;i++) {
      String loopingMessage = ("You can only desire based on what you know: " + i);
      CiphertextMessage bobLoopingMessage = bobSessionCipher.encrypt(loopingMessage.getBytes());

      byte[] loopingPlaintext = aliceSessionCipher.decrypt(new WhisperMessage(bobLoopingMessage.serialize()));
      assertTrue(new String(loopingPlaintext).equals(loopingMessage));
    }

    for (Pair<String, CiphertextMessage> aliceOutOfOrderMessage : aliceOutOfOrderMessages) {
      byte[] outOfOrderPlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceOutOfOrderMessage.second().serialize()));
      assertTrue(new String(outOfOrderPlaintext).equals(aliceOutOfOrderMessage.first()));
    }
  }


}
