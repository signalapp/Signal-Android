package org.whispersystems.test;

import android.test.AndroidTestCase;

import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.SessionState;
import org.whispersystems.libaxolotl.SessionStore;
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
    SessionState aliceSessionState = new InMemorySessionState();
    SessionState  bobSessionState   = new InMemorySessionState();

    initializeSessions(aliceSessionState, bobSessionState);

    SessionStore aliceSessionStore = new InMemorySessionStore(aliceSessionState);
    SessionStore  bobSessionStore   = new InMemorySessionStore(bobSessionState);

    SessionCipher aliceCipher       = new SessionCipher(aliceSessionStore);
    SessionCipher bobCipher         = new SessionCipher(bobSessionStore);

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
