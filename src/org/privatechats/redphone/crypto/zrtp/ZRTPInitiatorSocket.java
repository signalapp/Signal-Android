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

package org.privatechats.redphone.crypto.zrtp;

import android.content.Context;

import org.privatechats.redphone.crypto.SecureRtpSocket;
import org.privatechats.redphone.crypto.zrtp.retained.InitiatorRetainedSecretsCalculator;
import org.privatechats.redphone.crypto.zrtp.retained.RetainedSecrets;
import org.privatechats.redphone.crypto.zrtp.retained.RetainedSecretsCalculator;
import org.privatechats.redphone.crypto.zrtp.retained.RetainedSecretsDerivatives;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The "initiator" side of a ZRTP handshake.  This side just hangs out and waits
 * for the "responder" to send a hello packet, then proceeds through the ZRTP handshake.
 *
 * @author Moxie Marlinspike
 *
 */

public class ZRTPInitiatorSocket extends ZRTPSocket {

  private HelloPacket foreignHello;
  private HelloPacket localHello;
  private CommitPacket commitPacket;

  private DHPartOnePacket foreignDH;
  private DHPartTwoPacket localDH;

  private ConfirmOnePacket confirmPacket;

  private RetainedSecretsCalculator retainedSecretsCalculator;
  private boolean includeLegacyHeaderBug;

  public ZRTPInitiatorSocket(Context context, SecureRtpSocket socket,
                             byte[] localZid, String foreignNumber)
  {
    super(context, socket, localZid, foreignNumber, EXPECTING_HELLO);
    this.includeLegacyHeaderBug = false;
  }

  @Override
  protected void handleCommit(HandshakePacket packet) {
    throw new AssertionError("Invalid state!");
  }

  @Override
  protected void handleConfirmAck(HandshakePacket packet) {
    boolean continuity = retainedSecretsCalculator.hasContinuity(foreignDH.getDerivativeSecretOne(),
                                                                 foreignDH.getDerivativeSecretTwo());
    byte[] foreignZid  = foreignHello.getZID();
    byte[] rs1         = masterSecret.getRetainedSecret();
    long expiration    = System.currentTimeMillis() + (confirmPacket.getCacheTime() * 1000L);

//    cacheRetainedSecret(remoteNumber, foreignZid, rs1, expiration, continuity);
    setState(HANDSHAKE_COMPLETE);
  }

  @Override
  protected void handleConfirmOne(HandshakePacket packet) throws InvalidPacketException {
    confirmPacket = new ConfirmOnePacket(packet, isLegacyConfirmConnection());

    confirmPacket.verifyMac(masterSecret.getResponderMacKey());
    confirmPacket.decrypt(masterSecret.getResponderZrtpKey());

    byte[] preimage = confirmPacket.getPreimage();
    foreignDH.verifyMac(preimage);

    setState(EXPECTING_CONFIRM_ACK);
    sendFreshPacket(new ConfirmTwoPacket(masterSecret.getInitiatorMacKey(),
                                         masterSecret.getInitiatorZrtpKey(),
                                         this.hashChain, isLegacyConfirmConnection(),
                                         includeLegacyHeaderBug));
  }

  @Override
  protected void handleConfirmTwo(HandshakePacket packet) throws InvalidPacketException {
    throw new InvalidPacketException("Initiator received a Confirm2 packet?");
  }

  @Override
  protected void handleDH(HandshakePacket packet) throws InvalidPacketException {
    assert(localDH != null);

    SecretCalculator calculator;

    switch (getKeyAgreementType()) {
    case KA_TYPE_EC25:
      foreignDH  = new EC25DHPartOnePacket(packet, true);
      calculator = new EC25SecretCalculator();
      break;
    case KA_TYPE_DH3K:
      foreignDH  = new DH3KDHPartOnePacket(packet, true);
      calculator = new DH3KSecretCalculator();
      break;
    default:
      throw new AssertionError("Unknown KA type: " + getKeyAgreementType());
    }

    byte[] h1 = foreignDH.getHash();
    byte[] h2 = calculateH2(h1);

    foreignHello.verifyMac(h2);

    byte[] dhResult     = calculator.calculateKeyAgreement(getKeyPair(), foreignDH.getPvr());

    byte[] totalHash    = calculator.calculateTotalHash(foreignHello, commitPacket,
                                                        foreignDH, localDH);

    byte[] s1           = retainedSecretsCalculator.getS1(foreignDH.getDerivativeSecretOne(),
                                                          foreignDH.getDerivativeSecretTwo());

    byte[] sharedSecret = calculator.calculateSharedSecret(dhResult, totalHash, s1,
                                                           localHello.getZID(),
                                                           foreignHello.getZID());

    this.masterSecret   = new MasterSecret(sharedSecret, totalHash, localHello.getZID(),
                                           foreignHello.getZID());

    setState(EXPECTING_CONFIRM_ONE);
    sendFreshPacket(localDH);
  }

  @Override
  protected void handleHelloAck(HandshakePacket packet) throws InvalidPacketException {
//    RetainedSecrets retainedSecrets        = getRetainedSecrets(remoteNumber, foreignHello.getZID());
    RetainedSecrets retainedSecrets        = new RetainedSecrets(null, null);
    retainedSecretsCalculator              = new InitiatorRetainedSecretsCalculator(retainedSecrets);
    RetainedSecretsDerivatives derivatives = retainedSecretsCalculator.getRetainedSecretsDerivatives();

    switch (getKeyAgreementType()) {
    case KA_TYPE_EC25:
      localDH = new EC25DHPartTwoPacket(hashChain, getPublicKey(), derivatives, includeLegacyHeaderBug);
      break;
    case KA_TYPE_DH3K:
      localDH = new DH3KDHPartTwoPacket(hashChain, getPublicKey(), derivatives, includeLegacyHeaderBug);
      break;
    }

    commitPacket = new CommitPacket(hashChain, foreignHello.getMessageBytes(), localDH, localZid, includeLegacyHeaderBug);

    setState(EXPECTING_DH_1);
    sendFreshPacket(commitPacket);
  }

  @Override
  protected void handleHello(HandshakePacket packet) throws InvalidPacketException {
    foreignHello           = new HelloPacket(packet, true);
    includeLegacyHeaderBug = foreignHello.isLegacyHeaderBugPresent();
    localHello             = new HelloPacket(hashChain, localZid, includeLegacyHeaderBug);

    setState(EXPECTING_HELLO_ACK);
    sendFreshPacket(localHello);
  }

  private byte[] calculateH2(byte[] h1) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(h1);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public void negotiateStart() throws NegotiationFailedException {
    super.negotiateStart();
  }

  @Override
  protected int getKeyAgreementType() {
    if (foreignHello == null)
      throw new AssertionError("We can't project agreement type until we've seen a hello!");

    RedPhoneClientId foreignClientId = new RedPhoneClientId(foreignHello.getClientId());

    if (foreignClientId.isImplicitDh3kVersion() ||
        foreignHello.getKeyAgreementOptions().contains("EC25"))
    {
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
