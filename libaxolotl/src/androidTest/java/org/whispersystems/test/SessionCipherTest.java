package org.whispersystems.test;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;

import java.util.Arrays;

public class SessionCipherTest extends AndroidTestCase {

  public void testBasicSession()
      throws InvalidKeyException, DuplicateMessageException,
      LegacyMessageException, InvalidMessageException
  {
    SessionRecord aliceSessionRecord = new SessionRecord();
    SessionRecord bobSessionRecord   = new SessionRecord();

    initializeSessions(aliceSessionRecord.getSessionState(), bobSessionRecord.getSessionState());

    SessionStore aliceSessionStore = new InMemorySessionStore();
    SessionStore bobSessionStore   = new InMemorySessionStore();

    aliceSessionStore.store(2L, 1, aliceSessionRecord);
    bobSessionStore.store(3L, 1, bobSessionRecord);

    SessionCipher     aliceCipher    = new SessionCipher(aliceSessionStore, 2L, 1);
    SessionCipher     bobCipher      = new SessionCipher(bobSessionStore, 3L, 1);

    byte[]            alicePlaintext = "This is a plaintext message.".getBytes();
    CiphertextMessage message        = aliceCipher.encrypt(alicePlaintext);
    byte[]            bobPlaintext   = bobCipher.decrypt(message.serialize());

    assertTrue(Arrays.equals(alicePlaintext, bobPlaintext));

    byte[]            bobReply      = "This is a message from Bob.".getBytes();
    CiphertextMessage reply         = bobCipher.encrypt(bobReply);
    byte[]            receivedReply = aliceCipher.decrypt(reply.serialize());

    assertTrue(Arrays.equals(bobReply, receivedReply));
  }


  private void initializeSessions(SessionState aliceSessionState, SessionState bobSessionState)
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


    RatchetingSession.initializeSession(aliceSessionState, aliceBaseKey, bobBaseKey.getPublicKey(),
                                        aliceEphemeralKey, bobEphemeralKey.getPublicKey(),
                                        aliceIdentityKey, bobIdentityKey.getPublicKey());

    RatchetingSession.initializeSession(bobSessionState, bobBaseKey, aliceBaseKey.getPublicKey(),
                                        bobEphemeralKey, aliceEphemeralKey.getPublicKey(),
                                        bobIdentityKey, aliceIdentityKey.getPublicKey());
  }
}
