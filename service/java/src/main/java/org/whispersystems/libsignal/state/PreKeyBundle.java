/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.state;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;

/**
 * A class that contains a remote PreKey and collection
 * of associated items.
 *
 * @author Moxie Marlinspike
 */
public class PreKeyBundle {

  private int         registrationId;

  private int         deviceId;

  private int         preKeyId;
  private ECPublicKey preKeyPublic;

  private int         signedPreKeyId;
  private ECPublicKey signedPreKeyPublic;
  private byte[]      signedPreKeySignature;

  private IdentityKey identityKey;

  public PreKeyBundle(int registrationId, int deviceId, int preKeyId, ECPublicKey preKeyPublic,
                      int signedPreKeyId, ECPublicKey signedPreKeyPublic, byte[] signedPreKeySignature,
                      IdentityKey identityKey)
  {
    this.registrationId        = registrationId;
    this.deviceId              = deviceId;
    this.preKeyId              = preKeyId;
    this.preKeyPublic          = preKeyPublic;
    this.signedPreKeyId        = signedPreKeyId;
    this.signedPreKeyPublic    = signedPreKeyPublic;
    this.signedPreKeySignature = signedPreKeySignature;
    this.identityKey           = identityKey;
  }

  /**
   * @return the device ID this PreKey belongs to.
   */
  public int getDeviceId() {
    return deviceId;
  }

  /**
   * @return the unique key ID for this PreKey.
   */
  public int getPreKeyId() {
    return preKeyId;
  }

  /**
   * @return the public key for this PreKey.
   */
  public ECPublicKey getPreKey() {
    return preKeyPublic;
  }

  /**
   * @return the unique key ID for this signed prekey.
   */
  public int getSignedPreKeyId() {
    return signedPreKeyId;
  }

  /**
   * @return the signed prekey for this PreKeyBundle.
   */
  public ECPublicKey getSignedPreKey() {
    return signedPreKeyPublic;
  }

  /**
   * @return the signature over the signed  prekey.
   */
  public byte[] getSignedPreKeySignature() {
    return signedPreKeySignature;
  }

  /**
   * @return the {@link org.whispersystems.libsignal.IdentityKey} of this PreKeys owner.
   */
  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  /**
   * @return the registration ID associated with this PreKey.
   */
  public int getRegistrationId() {
    return registrationId;
  }
}
