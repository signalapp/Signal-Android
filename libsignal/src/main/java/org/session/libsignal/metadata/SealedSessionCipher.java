package org.session.libsignal.metadata;

import org.session.libsignal.metadata.certificate.CertificateValidator;
import org.session.libsignal.metadata.certificate.InvalidCertificateException;
import org.session.libsignal.metadata.certificate.SenderCertificate;
import org.session.libsignal.metadata.protocol.UnidentifiedSenderMessage;
import org.session.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;
import org.session.libsignal.libsignal.DuplicateMessageException;
import org.session.libsignal.libsignal.IdentityKey;
import org.session.libsignal.libsignal.IdentityKeyPair;
import org.session.libsignal.libsignal.InvalidKeyException;
import org.session.libsignal.libsignal.InvalidKeyIdException;
import org.session.libsignal.libsignal.InvalidMacException;
import org.session.libsignal.libsignal.InvalidMessageException;
import org.session.libsignal.libsignal.InvalidVersionException;
import org.session.libsignal.libsignal.LegacyMessageException;
import org.session.libsignal.libsignal.NoSessionException;
import org.session.libsignal.libsignal.SessionCipher;
import org.session.libsignal.libsignal.SignalProtocolAddress;
import org.session.libsignal.libsignal.UntrustedIdentityException;
import org.session.libsignal.libsignal.ecc.Curve;
import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.libsignal.ecc.ECPrivateKey;
import org.session.libsignal.libsignal.ecc.ECPublicKey;
import org.session.libsignal.libsignal.kdf.HKDFv3;
import org.session.libsignal.libsignal.loki.FallbackSessionCipher;
import org.session.libsignal.libsignal.protocol.CiphertextMessage;
import org.session.libsignal.libsignal.protocol.PreKeySignalMessage;
import org.session.libsignal.libsignal.protocol.SignalMessage;
import org.session.libsignal.libsignal.state.SignalProtocolStore;
import org.session.libsignal.libsignal.util.ByteUtil;
import org.session.libsignal.utilities.Hex;
import org.session.libsignal.libsignal.util.Pair;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SealedSessionCipher {

  private final SignalProtocolStore signalProtocolStore;
  private final SignalProtocolAddress localAddress;

  public SealedSessionCipher(SignalProtocolStore signalProtocolStore, SignalProtocolAddress localAddress)
  {
    this.signalProtocolStore  = signalProtocolStore;
    this.localAddress         = localAddress;
  }

  public byte[] encrypt(SignalProtocolAddress destinationAddress, SenderCertificate senderCertificate, byte[] paddedPlaintext)
      throws InvalidKeyException, UntrustedIdentityException
  {
      CiphertextMessage message = new SessionCipher(signalProtocolStore, destinationAddress).encrypt(paddedPlaintext);
      return encrypt(destinationAddress, senderCertificate, message);
  }

  public byte[] encrypt(SignalProtocolAddress destinationAddress, SenderCertificate senderCertificate, CiphertextMessage message)
      throws InvalidKeyException
  {
      try {
          IdentityKeyPair ourIdentity    = signalProtocolStore.getIdentityKeyPair();
          byte[]          theirPublicKey = Hex.fromStringCondensed(destinationAddress.getName());
          ECPublicKey     theirIdentity  = new IdentityKey(theirPublicKey, 0).getPublicKey();

          ECKeyPair     ephemeral           = Curve.generateKeyPair();
          byte[]        ephemeralSalt       = ByteUtil.combine("UnidentifiedDelivery".getBytes(), theirIdentity.serialize(), ephemeral.getPublicKey().serialize());
          EphemeralKeys ephemeralKeys       = calculateEphemeralKeys(theirIdentity, ephemeral.getPrivateKey(), ephemeralSalt);
          byte[]        staticKeyCiphertext = encrypt(ephemeralKeys.cipherKey, ephemeralKeys.macKey, ourIdentity.getPublicKey().serialize());

          byte[]                           staticSalt   = ByteUtil.combine(ephemeralKeys.chainKey, staticKeyCiphertext);
          StaticKeys                       staticKeys   = calculateStaticKeys(theirIdentity, ourIdentity.getPrivateKey(), staticSalt);
          UnidentifiedSenderMessageContent content      = new UnidentifiedSenderMessageContent(message.getType(), senderCertificate, message.serialize());
          byte[]                           messageBytes = encrypt(staticKeys.cipherKey, staticKeys.macKey, content.getSerialized());

          return new UnidentifiedSenderMessage(ephemeral.getPublicKey(), staticKeyCiphertext, messageBytes).getSerialized();
    } catch (IOException e) {
        throw new InvalidKeyException(e);
    }
  }

    /**
     * Decrypt a sealed session message.
     * This will return a Pair<Integer, byte[]> which is the CipherTextMessage type and the decrypted message content
     */
    public Pair<SignalProtocolAddress, Pair<Integer, byte[]>> decrypt(CertificateValidator validator, byte[] ciphertext, long timestamp, String prefixedPublicKey)
      throws
            InvalidMetadataMessageException, InvalidMetadataVersionException,
            ProtocolInvalidMessageException, ProtocolInvalidKeyException,
            ProtocolNoSessionException, ProtocolLegacyMessageException,
            ProtocolInvalidVersionException, ProtocolDuplicateMessageException,
            ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException,
            SelfSendException, IOException
  {
    UnidentifiedSenderMessageContent content;

    try {
      IdentityKeyPair           ourIdentity    = signalProtocolStore.getIdentityKeyPair();
      UnidentifiedSenderMessage wrapper        = new UnidentifiedSenderMessage(ciphertext);
      byte[]                    ephemeralSalt  = ByteUtil.combine("UnidentifiedDelivery".getBytes(), ourIdentity.getPublicKey().serialize(), wrapper.getEphemeral().serialize());
      EphemeralKeys             ephemeralKeys  = calculateEphemeralKeys(wrapper.getEphemeral(), ourIdentity.getPrivateKey(), ephemeralSalt);
      byte[]                    staticKeyBytes = decrypt(ephemeralKeys.cipherKey, ephemeralKeys.macKey, wrapper.getEncryptedStatic());

      ECPublicKey staticKey    = Curve.decodePoint(staticKeyBytes, 0);
      byte[]      staticSalt   = ByteUtil.combine(ephemeralKeys.chainKey, wrapper.getEncryptedStatic());
      StaticKeys  staticKeys   = calculateStaticKeys(staticKey, ourIdentity.getPrivateKey(), staticSalt);
      byte[]      messageBytes = decrypt(staticKeys.cipherKey, staticKeys.macKey, wrapper.getEncryptedMessage());

      content = new UnidentifiedSenderMessageContent(messageBytes);
      validator.validate(content.getSenderCertificate(), timestamp);

      if (content.getSenderCertificate().getSender().equals(localAddress.getName()) &&
          content.getSenderCertificate().getSenderDeviceId() == localAddress.getDeviceId())
      {
        throw new SelfSendException();
      }
    } catch (InvalidKeyException e) {
      throw new InvalidMetadataMessageException(e);
    } catch (InvalidMacException e) {
      throw new InvalidMetadataMessageException(e);
    } catch (InvalidCertificateException e) {
      throw new InvalidMetadataMessageException(e);
    }

    try {
        Pair<Integer, byte[]> dataPair = new Pair<>(content.getType(), decrypt(content));
        return new Pair<>(
            new SignalProtocolAddress(content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId()),
            dataPair
        );
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (InvalidKeyException e) {
      throw new ProtocolInvalidKeyException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (NoSessionException e) {
      throw new ProtocolNoSessionException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (LegacyMessageException e) {
      throw new ProtocolLegacyMessageException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (InvalidVersionException e) {
      throw new ProtocolInvalidVersionException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (DuplicateMessageException e) {
      throw new ProtocolDuplicateMessageException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (InvalidKeyIdException e) {
      throw new ProtocolInvalidKeyIdException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    } catch (UntrustedIdentityException e) {
      throw new ProtocolUntrustedIdentityException(e, content.getSenderCertificate().getSender(), content.getSenderCertificate().getSenderDeviceId());
    }
  }

  public int getSessionVersion(SignalProtocolAddress remoteAddress) {
    return new SessionCipher(signalProtocolStore, remoteAddress).getSessionVersion();
  }

  public int getRemoteRegistrationId(SignalProtocolAddress remoteAddress) {
    return new SessionCipher(signalProtocolStore, remoteAddress).getRemoteRegistrationId();
  }

  private EphemeralKeys calculateEphemeralKeys(ECPublicKey ephemeralPublic, ECPrivateKey ephemeralPrivate, byte[] salt) throws InvalidKeyException {
    try {
      byte[]   ephemeralSecret       = Curve.calculateAgreement(ephemeralPublic, ephemeralPrivate);
      byte[]   ephemeralDerived      = new HKDFv3().deriveSecrets(ephemeralSecret, salt, new byte[0], 96);
      byte[][] ephemeralDerivedParts = ByteUtil.split(ephemeralDerived, 32, 32, 32);

      return new EphemeralKeys(ephemeralDerivedParts[0], ephemeralDerivedParts[1], ephemeralDerivedParts[2]);
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  private StaticKeys calculateStaticKeys(ECPublicKey staticPublic, ECPrivateKey staticPrivate, byte[] salt) throws InvalidKeyException {
    try {
      byte[]      staticSecret       = Curve.calculateAgreement(staticPublic, staticPrivate);
      byte[]      staticDerived      = new HKDFv3().deriveSecrets(staticSecret, salt, new byte[0], 96);
      byte[][]    staticDerivedParts = ByteUtil.split(staticDerived, 32, 32, 32);

      return new StaticKeys(staticDerivedParts[1], staticDerivedParts[2]);
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] decrypt(UnidentifiedSenderMessageContent message)
      throws InvalidVersionException, InvalidMessageException, InvalidKeyException, DuplicateMessageException, InvalidKeyIdException, UntrustedIdentityException, LegacyMessageException, NoSessionException
  {

    SignalProtocolAddress sender = new SignalProtocolAddress(message.getSenderCertificate().getSender(), message.getSenderCertificate().getSenderDeviceId());

    switch (message.getType()) {
      case CiphertextMessage.WHISPER_TYPE: return new SessionCipher(signalProtocolStore, sender).decrypt(new SignalMessage(message.getContent()));
      case CiphertextMessage.PREKEY_TYPE:  return new SessionCipher(signalProtocolStore, sender).decrypt(new PreKeySignalMessage(message.getContent()));
      case CiphertextMessage.FALLBACK_MESSAGE_TYPE: {
          try {
              byte[] privateKey = signalProtocolStore.getIdentityKeyPair().getPrivateKey().serialize();
              return new FallbackSessionCipher(privateKey, sender.getName()).decrypt(message.getContent());
          } catch (Exception e) {
              throw new InvalidMessageException("Failed to decrypt fallback message.");
          }
      }
      default: throw new InvalidMessageException("Unknown type: " + message.getType());
    }
  }

  private byte[] encrypt(SecretKeySpec cipherKey, SecretKeySpec macKey, byte[] plaintext) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, cipherKey, new IvParameterSpec(new byte[16]));

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);

      byte[] ciphertext = cipher.doFinal(plaintext);
      byte[] ourFullMac = mac.doFinal(ciphertext);
      byte[] ourMac     = ByteUtil.trim(ourFullMac, 10);

      return ByteUtil.combine(ciphertext, ourMac);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] decrypt(SecretKeySpec cipherKey, SecretKeySpec macKey, byte[] ciphertext) throws InvalidMacException {
    try {
      if (ciphertext.length < 10) {
        throw new InvalidMacException("Ciphertext not long enough for MAC!");
      }

      byte[][] ciphertextParts = ByteUtil.split(ciphertext, ciphertext.length - 10, 10);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);

      byte[] digest   = mac.doFinal(ciphertextParts[0]);
      byte[] ourMac   = ByteUtil.trim(digest, 10);
      byte[] theirMac = ciphertextParts[1];

      if (!MessageDigest.isEqual(ourMac, theirMac)) {
        throw new InvalidMacException("Bad mac!");
      }

      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, cipherKey, new IvParameterSpec(new byte[16]));

      return cipher.doFinal(ciphertextParts[0]);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private static class EphemeralKeys {
    private final byte[]        chainKey;
    private final SecretKeySpec cipherKey;
    private final SecretKeySpec macKey;

    private EphemeralKeys(byte[] chainKey, byte[] cipherKey, byte[] macKey) {
      this.chainKey  = chainKey;
      this.cipherKey = new SecretKeySpec(cipherKey, "AES");
      this.macKey    = new SecretKeySpec(macKey, "HmacSHA256");
    }
  }

  private static class StaticKeys {
    private final SecretKeySpec cipherKey;
    private final SecretKeySpec macKey;

    private StaticKeys(byte[] cipherKey, byte[] macKey) {
      this.cipherKey = new SecretKeySpec(cipherKey, "AES");
      this.macKey    = new SecretKeySpec(macKey, "HmacSHA256");
    }
  }
}
