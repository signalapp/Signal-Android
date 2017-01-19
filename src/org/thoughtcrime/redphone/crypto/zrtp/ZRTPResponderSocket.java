/*
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

package org.thoughtcrime.redphone.crypto.zrtp;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.redphone.crypto.SecureRtpSocket;
import org.thoughtcrime.redphone.crypto.zrtp.retained.ResponderRetainedSecretsCalculator;
import org.thoughtcrime.redphone.crypto.zrtp.retained.RetainedSecrets;
import org.thoughtcrime.redphone.crypto.zrtp.retained.RetainedSecretsCalculator;
import org.thoughtcrime.redphone.crypto.zrtp.retained.RetainedSecretsDerivatives;

/**
 * The "responder" side of a ZRTP handshake.  We've received a signal from the
 * initiator, and they're waiting for us to kick off the handshake with a hello
 * message.
 *
 * @author Moxie Marlinspike
 *
 */

public class ZRTPResponderSocket extends ZRTPSocket {

  private HelloPacket localHello;
  private HelloPacket foreignHello;
  private CommitPacket foreignCommit;
  private DHPartOnePacket localDH;
  private DHPartTwoPacket foreignDH;

  private RetainedSecretsCalculator retainedSecretsCalculator;
  private boolean includeLegacyHeaderBug;

  public ZRTPResponderSocket(Context context, SecureRtpSocket socket,
                             byte[] localZid, String foreignNumber,
                             boolean includeLegacyHeaderBug)
  {
    super(context, socket, localZid, foreignNumber, EXPECTING_HELLO);
    Log.w("ZRTPResponderSocket", "includeLegacyHeaderBug: " + includeLegacyHeaderBug);
    this.includeLegacyHeaderBug = includeLegacyHeaderBug;
    this.localHello             = new HelloPacket(hashChain, localZid, includeLegacyHeaderBug);
  }

  @Override
  protected void handleHello(HandshakePacket packet) {
    foreignHello = new HelloPacket(packet, true);

    setState(EXPECTING_COMMIT);
    sendFreshPacket(new HelloAckPacket(includeLegacyHeaderBug));
  }

  @Override
  protected void handleCommit(HandshakePacket packet) throws InvalidPacketException {
    foreignCommit = new CommitPacket(packet, true);


//    RetainedSecrets retainedSecrets        = getRetainedSecrets(remoteNumber, foreignHello.getZID());
    RetainedSecrets retainedSecrets = new RetainedSecrets(null, null);
    retainedSecretsCalculator = new ResponderRetainedSecretsCalculator(retainedSecrets);
    RetainedSecretsDerivatives derivatives = retainedSecretsCalculator.getRetainedSecretsDerivatives();

    switch (getKeyAgreementType()) {
    case KA_TYPE_EC25: localDH = new EC25DHPartOnePacket(hashChain, getPublicKey(), derivatives, includeLegacyHeaderBug); break;
    case KA_TYPE_DH3K: localDH = new DH3KDHPartOnePacket(hashChain, getPublicKey(), derivatives, includeLegacyHeaderBug); break;
    }

    foreignHello.verifyMac(foreignCommit.getHash());

    setState(EXPECTING_DH_2);
    sendFreshPacket(localDH);
  }

  @Override
  protected void handleDH(HandshakePacket packet) throws InvalidPacketException {
    SecretCalculator calculator;

    switch (getKeyAgreementType()) {
    case KA_TYPE_EC25:
      foreignDH  = new EC25DHPartTwoPacket(packet, true);
      calculator = new EC25SecretCalculator();
      break;
    case KA_TYPE_DH3K:
      foreignDH  = new DH3KDHPartTwoPacket(packet, true);
      calculator = new DH3KSecretCalculator();
      break;
    default:
      throw new AssertionError("Unknown KA type: " + getKeyAgreementType());
    }

    foreignCommit.verifyMac(foreignDH.getHash());
    foreignCommit.verifyHvi(localHello.getMessageBytes(), foreignDH.getMessageBytes());

    byte[] dhResult     = calculator.calculateKeyAgreement(getKeyPair(), foreignDH.getPvr());

    byte[] totalHash    = calculator.calculateTotalHash(localHello, foreignCommit,
                                                        localDH, foreignDH);

    byte[] s1           = retainedSecretsCalculator.getS1(foreignDH.getDerivativeSecretOne(),
                                                          foreignDH.getDerivativeSecretTwo());

    byte[] sharedSecret = calculator.calculateSharedSecret(dhResult, totalHash, s1,
                                                           foreignHello.getZID(),
                                                           localHello.getZID());

    this.masterSecret   = new MasterSecret(sharedSecret, totalHash, foreignHello.getZID(),
                                           localHello.getZID());

    setState(EXPECTING_CONFIRM_TWO);
    sendFreshPacket(new ConfirmOnePacket(masterSecret.getResponderMacKey(),
                                         masterSecret.getResponderZrtpKey(),
                                         this.hashChain, isLegacyConfirmConnection(),
                                         includeLegacyHeaderBug));
  }

  @Override
  protected void handleConfirmTwo(HandshakePacket packet) throws InvalidPacketException {
    ConfirmTwoPacket confirmPacket = new ConfirmTwoPacket(packet, isLegacyConfirmConnection());

    confirmPacket.verifyMac(masterSecret.getInitiatorMacKey());
    confirmPacket.decrypt(masterSecret.getInitiatorZrtpKey());

    byte[] preimage = confirmPacket.getPreimage();
    foreignDH.verifyMac(preimage);

    setState(HANDSHAKE_COMPLETE);
    sendFreshPacket(new ConfAckPacket(includeLegacyHeaderBug));

    boolean continuity = retainedSecretsCalculator.hasContinuity(foreignDH.getDerivativeSecretOne(),
                                                                 foreignDH.getDerivativeSecretTwo());
    byte[] foreignZid  = foreignHello.getZID();
    byte[] rs1         = masterSecret.getRetainedSecret();
    long expiration    = System.currentTimeMillis() + (confirmPacket.getCacheTime() * 1000L);

//    cacheRetainedSecret(remoteNumber, foreignZid, rs1, expiration, continuity);
  }

  @Override
  protected void handleConfirmOne(HandshakePacket packet) throws InvalidPacketException {
    throw new InvalidPacketException("Responder received a Confirm1 Packet?");
  }

  @Override
  protected void handleHelloAck(HandshakePacket packet) {
    throw new AssertionError("Invalid state!");
  }

  @Override
  protected void handleConfirmAck(HandshakePacket packet) {
    throw new AssertionError("Invalid state!");
  }

  @Override
  public void negotiateStart() throws NegotiationFailedException {
    sendFreshPacket(localHello);
    super.negotiateStart();
  }

  @Override
  protected int getKeyAgreementType() {
    if (foreignCommit == null)
      throw new AssertionError("Can't determine KA until we've seen foreign commit!");

    String keyAgreementSpec = new String(foreignCommit.getKeyAgreementType());

    if (keyAgreementSpec.equals("EC25")) {
      return KA_TYPE_EC25;
    } else {
      return KA_TYPE_DH3K;
    }
  }

  @Override
  protected HelloPacket getForeignHello() {
    return foreignHello;
  }


}
