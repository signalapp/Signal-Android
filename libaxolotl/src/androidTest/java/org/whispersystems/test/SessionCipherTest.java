package org.whispersystems.test;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.whispersystems.libaxolotl.ratchet.RatchetingSession.AxolotlParameters;

public class SessionCipherTest extends AndroidTestCase {

  public void testBasicSessionV2()
      throws InvalidKeyException, DuplicateMessageException,
      LegacyMessageException, InvalidMessageException, NoSuchAlgorithmException, NoSessionException
  {
    SessionRecord aliceSessionRecord = new SessionRecord();
    SessionRecord bobSessionRecord   = new SessionRecord();

    initializeSessionsV2(aliceSessionRecord.getSessionState(), bobSessionRecord.getSessionState());
    runInteraction(aliceSessionRecord, bobSessionRecord);
  }

  public void testBasicSessionV3()
      throws InvalidKeyException, DuplicateMessageException,
      LegacyMessageException, InvalidMessageException, NoSuchAlgorithmException, NoSessionException
  {
    SessionRecord aliceSessionRecord = new SessionRecord();
    SessionRecord bobSessionRecord   = new SessionRecord();

    initializeSessionsV3(aliceSessionRecord.getSessionState(), bobSessionRecord.getSessionState());
    runInteraction(aliceSessionRecord, bobSessionRecord);
  }

  private void runInteraction(SessionRecord aliceSessionRecord, SessionRecord bobSessionRecord)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, NoSuchAlgorithmException, NoSessionException {
    SessionStore      aliceSessionStore      = new InMemorySessionStore();
    PreKeyStore       alicePreKeyStore       = new InMemoryPreKeyStore();
    SignedPreKeyStore aliceSignedPreKeyStore = new InMemorySignedPreKeyStore();
    IdentityKeyStore  aliceIdentityKeyStore  = new InMemoryIdentityKeyStore();
    SessionStore      bobSessionStore        = new InMemorySessionStore();
    PreKeyStore       bobPreKeyStore         = new InMemoryPreKeyStore();
    SignedPreKeyStore bobSignedPreKeyStore   = new InMemorySignedPreKeyStore();
    IdentityKeyStore  bobIdentityKeyStore    = new InMemoryIdentityKeyStore();

    aliceSessionStore.storeSession(2L, 1, aliceSessionRecord);
    bobSessionStore.storeSession(3L, 1, bobSessionRecord);

    SessionCipher     aliceCipher    = new SessionCipher(aliceSessionStore, alicePreKeyStore, aliceSignedPreKeyStore, aliceIdentityKeyStore, 2L, 1);
    SessionCipher     bobCipher      = new SessionCipher(bobSessionStore, bobPreKeyStore, bobSignedPreKeyStore, bobIdentityKeyStore, 3L, 1);

    byte[]            alicePlaintext = "This is a plaintext message.".getBytes();
    CiphertextMessage message        = aliceCipher.encrypt(alicePlaintext);
    byte[]            bobPlaintext   = bobCipher.decrypt(new WhisperMessage(message.serialize()));

    assertTrue(Arrays.equals(alicePlaintext, bobPlaintext));

    byte[]            bobReply      = "This is a message from Bob.".getBytes();
    CiphertextMessage reply         = bobCipher.encrypt(bobReply);
    byte[]            receivedReply = aliceCipher.decrypt(new WhisperMessage(reply.serialize()));

    assertTrue(Arrays.equals(bobReply, receivedReply));

    List<CiphertextMessage> aliceCiphertextMessages = new ArrayList<>();
    List<byte[]>            alicePlaintextMessages  = new ArrayList<>();

    for (int i=0;i<50;i++) {
      alicePlaintextMessages.add(("смерть за смерть " + i).getBytes());
      aliceCiphertextMessages.add(aliceCipher.encrypt(("смерть за смерть " + i).getBytes()));
    }

    long seed = System.currentTimeMillis();

    Collections.shuffle(aliceCiphertextMessages, new Random(seed));
    Collections.shuffle(alicePlaintextMessages, new Random(seed));

    for (int i=0;i<aliceCiphertextMessages.size() / 2;i++) {
      byte[] receivedPlaintext = bobCipher.decrypt(new WhisperMessage(aliceCiphertextMessages.get(i).serialize()));
      assertTrue(Arrays.equals(receivedPlaintext, alicePlaintextMessages.get(i)));
    }

    List<CiphertextMessage> bobCiphertextMessages = new ArrayList<>();
    List<byte[]>            bobPlaintextMessages  = new ArrayList<>();

    for (int i=0;i<20;i++) {
      bobPlaintextMessages.add(("смерть за смерть " + i).getBytes());
      bobCiphertextMessages.add(bobCipher.encrypt(("смерть за смерть " + i).getBytes()));
    }

    seed = System.currentTimeMillis();

    Collections.shuffle(bobCiphertextMessages, new Random(seed));
    Collections.shuffle(bobPlaintextMessages, new Random(seed));

    for (int i=0;i<bobCiphertextMessages.size() / 2;i++) {
      byte[] receivedPlaintext = aliceCipher.decrypt(new WhisperMessage(bobCiphertextMessages.get(i).serialize()));
      assertTrue(Arrays.equals(receivedPlaintext, bobPlaintextMessages.get(i)));
    }

    for (int i=aliceCiphertextMessages.size()/2;i<aliceCiphertextMessages.size();i++) {
      byte[] receivedPlaintext = bobCipher.decrypt(new WhisperMessage(aliceCiphertextMessages.get(i).serialize()));
      assertTrue(Arrays.equals(receivedPlaintext, alicePlaintextMessages.get(i)));
    }

    for (int i=bobCiphertextMessages.size() / 2;i<bobCiphertextMessages.size();i++) {
      byte[] receivedPlaintext = aliceCipher.decrypt(new WhisperMessage(bobCiphertextMessages.get(i).serialize()));
      assertTrue(Arrays.equals(receivedPlaintext, bobPlaintextMessages.get(i)));
    }
  }


  private void initializeSessionsV2(SessionState aliceSessionState, SessionState bobSessionState)
      throws InvalidKeyException
  {
    ECKeyPair       aliceIdentityKeyPair = Curve.generateKeyPair(false);
    IdentityKeyPair aliceIdentityKey     = new IdentityKeyPair(new IdentityKey(aliceIdentityKeyPair.getPublicKey()),
                                                               aliceIdentityKeyPair.getPrivateKey());
    ECKeyPair       aliceBaseKey         = Curve.generateKeyPair(true);
    ECKeyPair       aliceEphemeralKey    = Curve.generateKeyPair(true);

    ECKeyPair       bobIdentityKeyPair   = Curve.generateKeyPair(false);
    IdentityKeyPair bobIdentityKey       = new IdentityKeyPair(new IdentityKey(bobIdentityKeyPair.getPublicKey()),
                                                               bobIdentityKeyPair.getPrivateKey());
    ECKeyPair       bobBaseKey           = Curve.generateKeyPair(true);
    ECKeyPair       bobEphemeralKey      = bobBaseKey;

    AxolotlParameters aliceParameters =
        AxolotlParameters.newBuilder()
                                .setOurIdentityKey(aliceIdentityKey)
                                .setOurBaseKey(aliceBaseKey)
                                .setOurEphemeralKey(aliceEphemeralKey)
                                .setOurPreKey(Optional.<ECKeyPair>absent())
                                .setTheirIdentityKey(bobIdentityKey.getPublicKey())
                                .setTheirBaseKey(bobBaseKey.getPublicKey())
                                .setTheirEphemeralKey(bobEphemeralKey.getPublicKey())
                                .setTheirPreKey(Optional.<ECPublicKey>absent())
                                .create();

    AxolotlParameters bobParameters =
        RatchetingSession.AxolotlParameters.newBuilder()
                                .setOurIdentityKey(bobIdentityKey)
                                .setOurBaseKey(bobBaseKey)
                                .setOurEphemeralKey(bobEphemeralKey)
                                .setOurPreKey(Optional.<ECKeyPair>absent())
                                .setTheirIdentityKey(aliceIdentityKey.getPublicKey())
                                .setTheirBaseKey(aliceBaseKey.getPublicKey())
                                .setTheirEphemeralKey(aliceEphemeralKey.getPublicKey())
                                .setTheirPreKey(Optional.<ECPublicKey>absent())
                                .create();


    RatchetingSession.initializeSession(aliceSessionState, 2, aliceParameters);
    RatchetingSession.initializeSession(bobSessionState, 2, bobParameters);
  }

  private void initializeSessionsV3(SessionState aliceSessionState, SessionState bobSessionState)
      throws InvalidKeyException
  {
    ECKeyPair       aliceIdentityKeyPair = Curve.generateKeyPair(false);
    IdentityKeyPair aliceIdentityKey     = new IdentityKeyPair(new IdentityKey(aliceIdentityKeyPair.getPublicKey()),
                                                               aliceIdentityKeyPair.getPrivateKey());
    ECKeyPair       aliceBaseKey         = Curve.generateKeyPair(true);
    ECKeyPair       aliceEphemeralKey    = Curve.generateKeyPair(true);

    ECKeyPair       alicePreKey          = aliceBaseKey;

    ECKeyPair       bobIdentityKeyPair   = Curve.generateKeyPair(false);
    IdentityKeyPair bobIdentityKey       = new IdentityKeyPair(new IdentityKey(bobIdentityKeyPair.getPublicKey()),
                                                               bobIdentityKeyPair.getPrivateKey());
    ECKeyPair       bobBaseKey           = Curve.generateKeyPair(true);
    ECKeyPair       bobEphemeralKey      = bobBaseKey;

    ECKeyPair       bobPreKey            = Curve.generateKeyPair(true);

    AxolotlParameters aliceParameters =
        AxolotlParameters.newBuilder()
                                .setOurIdentityKey(aliceIdentityKey)
                                .setOurBaseKey(aliceBaseKey)
                                .setOurEphemeralKey(aliceEphemeralKey)
                                .setOurPreKey(Optional.of(alicePreKey))
                                .setTheirIdentityKey(bobIdentityKey.getPublicKey())
                                .setTheirBaseKey(bobBaseKey.getPublicKey())
                                .setTheirEphemeralKey(bobEphemeralKey.getPublicKey())
                                .setTheirPreKey(Optional.of(bobPreKey.getPublicKey()))
                                .create();

    AxolotlParameters bobParameters =
        AxolotlParameters.newBuilder()
                                .setOurIdentityKey(bobIdentityKey)
                                .setOurBaseKey(bobBaseKey)
                                .setOurEphemeralKey(bobEphemeralKey)
                                .setOurPreKey(Optional.of(bobPreKey))
                                .setTheirIdentityKey(aliceIdentityKey.getPublicKey())
                                .setTheirBaseKey(aliceBaseKey.getPublicKey())
                                .setTheirEphemeralKey(aliceEphemeralKey.getPublicKey())
                                .setTheirPreKey(Optional.of(alicePreKey.getPublicKey()))
                                .create();


    RatchetingSession.initializeSession(aliceSessionState, 3, aliceParameters);
    RatchetingSession.initializeSession(bobSessionState, 3, bobParameters);

  }
}
